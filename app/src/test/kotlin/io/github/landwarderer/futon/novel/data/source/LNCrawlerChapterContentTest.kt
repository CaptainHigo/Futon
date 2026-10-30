package io.github.landwarderer.futon.novel.data.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.io.IOException

class LNCrawlerChapterContentTest {

	private val chapterUrl = "https://api.lncrawler.monster/novels/example/source/chapter/3/"

	@Test
	fun resolvesArchiveImagesAndPreservesOrderForDownload() {
		val html = LNCrawlerChapterContent.normalize(
			"<h1>Chapter 3</h1><p><img src='images/first.jpg'></p><img src='./images/second.jpg'>",
			"https://media.example/novels/example/images",
			chapterUrl,
		)
		val pages = LNCrawlerChapterContent.downloadPages(listOf(page(html)))
		assertEquals(
			listOf("https://media.example/novels/example/images/first.jpg",
				"https://media.example/novels/example/images/second.jpg"),
			pages.map { it.url },
		)
		assertEquals(2, pages.map { it.id }.toSet().size)
	}

	@Test
	fun supportsRelativeImageDirectoryAndAbsoluteImages() {
		val html = LNCrawlerChapterContent.normalize(
			"<img src='images/a.jpg'><img src='https://cdn.example/b.jpg'><img src='//cdn.example/c.jpg'>",
			"/media/book/images/",
			chapterUrl,
		)
		assertEquals(
			listOf("https://api.lncrawler.monster/media/book/images/a.jpg",
				"https://cdn.example/b.jpg", "https://cdn.example/c.jpg"),
			Jsoup.parseBodyFragment(html).select("img").map { it.attr("src") },
		)
	}

	@Test
	fun preservesNovelTextAndStripsExecutableMarkup() {
		val html = LNCrawlerChapterContent.normalize(
			"<p>Story text</p><img src='https://cdn.example/a.jpg' onerror='alert(1)'>" +
				"<script>alert(2)</script><iframe src='https://example.com'></iframe>",
			null,
			chapterUrl,
		)
		assertEquals("Story text", Jsoup.parseBodyFragment(html).text())
		assertFalse(html.contains("alert"))
		assertFalse(html.contains("iframe"))
	}

	@Test(expected = IOException::class)
	fun missingArchiveDirectoryDoesNotProduceGuessedDownloadUrls() {
		val html = LNCrawlerChapterContent.normalize("<img src='images/a.jpg'>", null, chapterUrl)
		LNCrawlerChapterContent.downloadPages(listOf(page(html)))
	}

	@Test(expected = IOException::class)
	fun mixedTextAndImagesCannotBeSilentlySavedAsImagesOnly() {
		LNCrawlerChapterContent.downloadPages(listOf(page("<p>Story text</p><img src='https://cdn.example/a.jpg'>")))
	}

	private fun page(html: String) = MangaPage(
		id = 42L,
		url = html,
		preview = null,
		source = NovelParserSource.LNCRAWLER,
	)
}
