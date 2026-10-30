package io.github.landwarderer.futon.novel.data.source

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.io.IOException

internal object LNCrawlerChapterContent {

	fun normalize(html: String, imagesPath: String?, chapterUrl: String): String {
		val chapterBase = chapterUrl.toHttpUrlOrNull()
		val imageBase = imagesPath?.takeIf { it.isNotBlank() && it != "null" }
			?.let { chapterBase?.resolve(it.trimEnd('/') + "/") }
		val doc = Jsoup.parseBodyFragment(html, chapterUrl)
		doc.select("script, style, iframe, object, embed, form, base").remove()
		for (image in doc.select("img")) {
			val src = image.attr("src").trim().removePrefix("./")
			val resolved = when {
				src.isEmpty() -> null
				src.startsWith("images/") -> imageBase?.resolve(src.removePrefix("images/"))
				src.toHttpUrlOrNull() != null -> src.toHttpUrlOrNull()
				src.startsWith("/") -> chapterBase?.resolve(src)
				else -> imageBase?.resolve(src)
			}
			if (resolved != null) {
				image.attr("src", resolved.toString())
			} else {
				// Keep the alt text visible, without requesting a guessed image address.
				image.removeAttr("src")
			}
		}
		return Jsoup.clean(doc.body().html(), chapterUrl, Safelist.relaxed())
	}

	fun downloadPages(pages: List<MangaPage>): List<MangaPage> = pages.flatMap { page ->
		val doc = Jsoup.parseBodyFragment(page.url)
		val images = doc.select("img")
		val text = doc.body().clone()
		text.select("h1, h2, h3, h4, h5, h6, img").remove()
		if (images.isEmpty() || text.text().isNotBlank()) {
			throw IOException("LNCrawler : ce chapitre contient du texte. Le téléchargement en images ne le prend pas en charge.")
		}
		images.mapIndexed { index, image ->
			val url = image.attr("src").toHttpUrlOrNull()?.toString()
				?: throw IOException("LNCrawler : adresse d’image manquante dans ce chapitre.")
			page.copy(id = "${page.id}_$index".longHashCode(), url = url, preview = null)
		}
	}
}
