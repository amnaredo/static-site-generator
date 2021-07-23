import $ivy.`com.lihaoyi::scalatags:0.9.1`, scalatags.Text.all._
import $ivy.`com.atlassian.commonmark:commonmark:0.13.1`
import mill._

def mdNameToHtml(name: String) = name.replace(" ", "-").toLowerCase +  ".html"

val postInfo = interp.watchValue {
    os.list(os.pwd / "post")
    .map { p => 
        val s"$prefix - $suffix.md" = p.last
        (prefix, suffix, p)
    }
    .sortBy(_._1.toInt)
}

def bootstrap = T{
    os.write(
        T.dest / "bootstrap.css",
        requests.get("https://stackpath.bootstrapcdn.com/bootstrap/4.5.0/css/bootstrap.css")
            .text()
    )
    PathRef(T.dest / "bootstrap.css")
}

object post extends Cross[PostModule](postInfo.map(_._1):_*)
class PostModule(number: String) extends Module{
    val Some((_, suffix, markdownPath)) = postInfo.find(_._1 == number)
    def path = T.source(markdownPath)
    def preview = T{
        val parser = org.commonmark.parser.Parser.builder().build()
        val firstPara = os.read.lines(path().path).takeWhile(_.nonEmpty)
        val document = parser.parse(firstPara.mkString("\n"))
        val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
        val output = renderer.render(document)
        output
    }
    def render = T{ 
        val parser = org.commonmark.parser.Parser.builder().build()
        val document = parser.parse(os.read(path().path))
        val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
        val output = renderer.render(document)
        os.write(
            T.dest / mdNameToHtml(suffix),
            doctype("html")(
                html(
                    head(link(rel := "stylesheet", href := "../bootstrap")),
                    body(
                        h1(a(href := "../index.html")("Blog"), " / ", suffix), 
                        raw(output)
                    )
                )
            )
        )
        PathRef(T.dest / mdNameToHtml(suffix))
    }
}

def links = T.input{ postInfo.map(_._2) }
val previews = T.sequence(postInfo.map(_._1).map(post(_).preview))
val posts = T.sequence(postInfo.map(_._1).map(post(_).render))

def index = T {
    os.write(
        T.dest / "index.html",
        doctype("html")(
            html(
                head(link(rel := "stylesheet", href := "../bootstrap.css")),
                body(
                    h1("Blog"),
                    for ((suffix, preview) <- links().zip(previews()))
                    yield frag(
                        h2(a(href := ("post/" + mdNameToHtml(suffix)), suffix)),
                        raw(preview) // include markdown-generated HTML "raw" without HTML-escaping it
                    )
                )
            )
        )
    )
    PathRef(T.dest / "index.html")
}

def dist = T {
    for (post <- posts()) {
        os.copy(post.path, T.dest / "post" / post.path.last, createFolders = true)
    }
    os.copy(index().path, T.dest / "index.html")
    os.copy(bootstrap().path, T.dest / "bootstrap.css")
    PathRef(T.dest)
}