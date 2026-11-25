package com.agyide

import android.app.Application
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

class AntigravityIdeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initTextMate()
    }

    private fun initTextMate() {
        runCatching {
            val fileProviderRegistry = FileProviderRegistry.getInstance()
            fileProviderRegistry.addFileProvider(AssetsFileResolver(assets))

            val themeRegistry = ThemeRegistry.getInstance()
            val themes = listOf(
                "antigravity_cosmic" to true,
                "darcula" to true,
                "abyss" to true,
                "solarized_dark" to true,
                "quietlight" to false
            )
            themes.forEach { (name, isDarkTheme) ->
                val path = "textmate/$name.json"
                val stream = fileProviderRegistry.tryGetInputStream(path)
                if (stream != null) {
                    val source = IThemeSource.fromInputStream(stream, path, null)
                    themeRegistry.loadTheme(
                        ThemeModel(source, name).apply { isDark = isDarkTheme }
                    )
                }
            }
            themeRegistry.setTheme("antigravity_cosmic")
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }
    }
}
