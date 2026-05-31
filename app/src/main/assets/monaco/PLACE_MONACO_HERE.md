# Place the Monaco Editor distribution here

The Monaco editor host page (`editor.html`) loads the editor from local assets
when present, and otherwise falls back to a CDN
(`https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs`).

For **fully offline** use, you must drop the Monaco `min/vs` folder into:

```
app/src/main/assets/monaco/vs/
```

so that the AMD loader is reachable at:

```
file:///android_asset/monaco/vs/loader.js
```

## How to obtain `min/vs`

1. Download the npm package tarball (version 0.52.2):

   ```
   https://registry.npmjs.org/monaco-editor/-/monaco-editor-0.52.2.tgz
   ```

2. Extract the tarball. Inside you will find `package/min/vs/`.

3. Copy the **contents** of `package/min/vs/` into `app/src/main/assets/monaco/vs/`.

   The result should look like:

   ```
   app/src/main/assets/monaco/vs/loader.js
   app/src/main/assets/monaco/vs/editor/editor.main.js
   app/src/main/assets/monaco/vs/editor/editor.main.css
   app/src/main/assets/monaco/vs/base/...
   app/src/main/assets/monaco/vs/language/...
   ...
   ```

> Use the package's `min/vs` (minified) folder, not `dev/vs`, to keep the APK
> size down (~5 MB). Once present, the editor works with no network access.

If this folder is absent, the editor still works on a networked device via the
CDN fallback described above.
