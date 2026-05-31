package com.andcodedit.dex

import androidx.compose.runtime.mutableStateOf
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import java.io.File

/**
 * Parses DEX/APK files with dexlib2 and exposes the result as Compose state for
 * the DEX Mode UI: a class browser, per-class method list, and an editable
 * Smali view.
 *
 * Reassembly (Smali -> DEX -> APK -> re-sign) is intentionally a documented stub
 * here; the full pipeline uses `com.android.tools.smali:smali` to assemble and
 * dexlib2 to rebuild, then zip-replace + apksigner.
 */
class DexParserService {

    var currentDexFile: DexFile? = null
        private set

    val classes = mutableStateOf<List<ClassDef>>(emptyList())
    val selectedClass = mutableStateOf<ClassDef?>(null)
    val methods = mutableStateOf<List<Method>>(emptyList())
    val smaliContent = mutableStateOf("")
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    /** Load a `.dex` or `.apk` from disk and populate the class list. */
    fun loadDexFile(filePath: String) {
        isLoading.value = true
        error.value = null
        try {
            val file = File(filePath)
            if (!file.exists()) {
                error.value = "File not found: $filePath"
                return
            }
            // dexlib2 loads both raw .dex and .apk containers.
            currentDexFile = DexFileFactory.loadDexFile(file, Opcodes.getDefault())
            classes.value = currentDexFile?.classes?.toList() ?: emptyList()
            selectedClass.value = null
            methods.value = emptyList()
            smaliContent.value = ""
        } catch (e: Exception) {
            error.value = "Failed to parse DEX: ${e.message}"
        } finally {
            isLoading.value = false
        }
    }

    /** Select a class, populate its methods, and render a lightweight Smali view. */
    fun selectClass(classDef: ClassDef) {
        selectedClass.value = classDef
        methods.value = classDef.methods.toList()
        smaliContent.value = renderSmali(classDef)
    }

    fun updateSmali(newContent: String) {
        smaliContent.value = newContent
    }

    /**
     * Stub for the reassembly pipeline. Production flow:
     *  1. Assemble edited Smali -> DEX with com.android.tools.smali:smali.
     *  2. Rebuild a DexFile / merge with dexlib2.
     *  3. Replace classes.dex inside the APK (zip).
     *  4. Re-sign with apksigner.
     */
    fun reassembleAndSave(outputPath: String): Boolean {
        return try {
            currentDexFile ?: return false
            // Intentionally not writing yet — see KDoc. Return true so the UI
            // can demonstrate the flow without producing an invalid APK.
            true
        } catch (e: Exception) {
            error.value = e.message
            false
        }
    }

    fun clear() {
        currentDexFile = null
        classes.value = emptyList()
        selectedClass.value = null
        methods.value = emptyList()
        smaliContent.value = ""
        error.value = null
    }

    private fun renderSmali(classDef: ClassDef): String = buildString {
        append(".class ${classDef.accessFlags.toString(16)} ${classDef.type}\n")
        append(".super ${classDef.superclass ?: "Ljava/lang/Object;"}\n\n")
        classDef.methods.forEach { m ->
            val params = m.parameterTypes.joinToString("")
            append(".method ${m.name}($params)${m.returnType}\n")
            append("    .registers ${m.implementation?.registerCount ?: 0}\n")
            append("    # method body\n")
            append(".end method\n\n")
        }
    }
}
