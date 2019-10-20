package top.shenluw.plugin.dubbo.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * @author Shenluw
 * created：2019/10/20 21:03
 */
object TempFileManager : Disposable {

    private val files = arrayListOf<String>()

    fun createTempFileIfNotExists(name: String, extension: String): File? {
        val dir = File(FileUtil.getTempDirectory())
        val f = File(dir, name + extension)
        if (f.exists() && f.isFile) {
            return f
        }
        try {
            dir.mkdirs()
            if (f.createNewFile()) {
                return f
            }
        } catch (e: Exception) {
        }
        return null
    }

    override fun dispose() {
        files.forEach {
            val f = File(it)
            if (f.exists()) {
                f.delete()
            }
        }
        files.clear()
    }

}