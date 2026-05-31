//! Production PTY backend for the ANDCODEDIT terminal, exposed to Kotlin via UniFFI.
//!
//! This crate wraps [`portable-pty`] to spawn a shell inside a pseudo-terminal,
//! pumps the shell's output to a Kotlin callback on a background thread, and
//! exposes `write_input` / `resize` to drive the session.

use std::io::{Read, Write};
use std::sync::{Arc, Mutex};
use std::thread;

use portable_pty::{native_pty_system, CommandBuilder, MasterPty, PtySize};

/// Configuration for a new terminal session.
#[derive(Debug, Clone)]
pub struct TerminalConfig {
    pub rows: u16,
    pub cols: u16,
    pub shell: String,
}

/// Errors that can occur while creating or driving a terminal session.
///
/// Each variant carries a human-readable message describing what failed.
#[derive(Debug, thiserror::Error)]
pub enum TerminalError {
    /// Failed to spawn the shell process inside the PTY.
    #[error("spawn error: {message}")]
    Spawn { message: String },
    /// An I/O error occurred while reading from or writing to the PTY.
    #[error("io error: {message}")]
    Io { message: String },
    /// A PTY-level error occurred (open, clone, resize, etc.).
    #[error("pty error: {message}")]
    Pty { message: String },
}

/// Callback used to deliver raw PTY output bytes to the Kotlin side.
///
/// Implemented on the foreign (Kotlin) side; invoked from the reader thread.
pub trait TerminalOutputCallback: Send + Sync {
    fn on_output(&self, data: Vec<u8>);
}

/// A live terminal session backed by a pseudo-terminal.
///
/// Holds the PTY master (for resizing) and the writer (for input). The reader
/// runs on a detached background thread that forwards output to the callback.
pub struct TerminalSession {
    master: Arc<Mutex<Box<dyn MasterPty + Send>>>,
    writer: Arc<Mutex<Box<dyn Write + Send>>>,
}

impl TerminalSession {
    /// Send bytes to the shell's standard input.
    pub fn write_input(&self, data: Vec<u8>) -> Result<(), TerminalError> {
        let mut writer = self
            .writer
            .lock()
            .map_err(|e| TerminalError::Io { message: format!("writer lock poisoned: {e}") })?;
        writer
            .write_all(&data)
            .map_err(|e| TerminalError::Io { message: e.to_string() })?;
        writer
            .flush()
            .map_err(|e| TerminalError::Io { message: e.to_string() })?;
        Ok(())
    }

    /// Resize the pseudo-terminal to the given dimensions.
    pub fn resize(&self, rows: u16, cols: u16) -> Result<(), TerminalError> {
        let master = self
            .master
            .lock()
            .map_err(|e| TerminalError::Pty { message: format!("master lock poisoned: {e}") })?;
        master
            .resize(PtySize {
                rows,
                cols,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(|e| TerminalError::Pty { message: e.to_string() })?;
        Ok(())
    }
}

/// Create a new terminal session: open a PTY, spawn the shell, and start
/// pumping output to `callback`.
pub fn create_terminal_session(
    config: TerminalConfig,
    callback: Box<dyn TerminalOutputCallback>,
) -> Result<Arc<TerminalSession>, TerminalError> {
    let pty_system = native_pty_system();

    let pair = pty_system
        .openpty(PtySize {
            rows: config.rows,
            cols: config.cols,
            pixel_width: 0,
            pixel_height: 0,
        })
        .map_err(|e| TerminalError::Pty { message: e.to_string() })?;

    // Choose the shell, falling back to the Android system shell.
    let shell = if config.shell.is_empty() {
        "/system/bin/sh".to_string()
    } else {
        config.shell.clone()
    };
    let cmd = CommandBuilder::new(shell);

    // Spawn the shell attached to the slave end of the PTY. The returned child
    // is dropped here; the process stays alive because the PTY keeps it open.
    let _child = pair
        .slave
        .spawn_command(cmd)
        .map_err(|e| TerminalError::Spawn { message: e.to_string() })?;

    // Clone a reader from the master so the background thread can read output
    // independently of the writer.
    let mut reader = pair
        .master
        .try_clone_reader()
        .map_err(|e| TerminalError::Pty { message: e.to_string() })?;

    // Take the writer out of the master for input.
    let writer = pair
        .master
        .take_writer()
        .map_err(|e| TerminalError::Pty { message: e.to_string() })?;

    // Reader thread: forward PTY output to the Kotlin callback until EOF/error.
    thread::spawn(move || {
        let mut buf = [0u8; 4096];
        loop {
            match reader.read(&mut buf) {
                Ok(0) => break, // EOF: shell exited.
                Ok(n) => callback.on_output(buf[..n].to_vec()),
                Err(e) => {
                    log::warn!("terminal reader thread stopping: {e}");
                    break;
                }
            }
        }
    });

    Ok(Arc::new(TerminalSession {
        master: Arc::new(Mutex::new(pair.master)),
        writer: Arc::new(Mutex::new(writer)),
    }))
}

uniffi::include_scaffolding!("andcodedit_terminal");
