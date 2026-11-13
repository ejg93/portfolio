package com.portfolio.cheongyak;

import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// 공용 StubServer 는 본문을 문자열로만 돌려줘서 PDF 바이트를 못 보낸다. 여기서 JDK HttpServer 를 따로 띄운다
class NoticeDocClientTest {

    private HttpServer server;
    private String base;
    private NoticeDocClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new NoticeDocClient();
        ReflectionTestUtils.setField(client, "sleeper", (NoticeDocClient.Sleeper) ms -> { });
        ReflectionTestUtils.setField(client, "attachLink", Pattern.compile(Pattern.quote(base) + "/att\\?[^\"'\\s<>]+"));
        // 가짜 서버가 127.0.0.1 이라 호스트 제한을 여기로 바꾼다
        ReflectionTestUtils.setField(client, "allowedHost", Pattern.compile("127\\.0\\.0\\.1"));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(String path, int status, String disposition, byte[] body) {
        server.createContext(path, ex -> {
            if (disposition != null) {
                ex.getResponseHeaders().add("Content-Disposition", disposition);
            }
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
    }

    /** 한 쪽짜리 PDF. 표준 글꼴은 한글을 못 쓰니 ASCII 문구만 넣는다 */
    private static byte[] pdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("applyhome 밖 호스트는 요청하지 않고 BAD_URL")
    void foreignHostIsBadUrl() {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/detail", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(500, -1);
        });
        ReflectionTestUtils.setField(client, "allowedHost", Pattern.compile("(?:[a-z0-9-]+\\.)*applyhome\\.co\\.kr"));

        assertThat(client.download(base + "/detail").status()).isEqualTo("BAD_URL");
        assertThat(hits).hasValue(0);
    }

    @Test
    @DisplayName("허용 호스트 밖으로 가는 리다이렉트는 따라가지 않고 BAD_URL")
    void redirectToForeignHostIsNotFollowed() {
        AtomicInteger hits = new AtomicInteger();
        String foreign = base.replace("127.0.0.1", "localhost");
        server.createContext("/out", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(500, -1);
        });
        server.createContext("/detail", ex -> {
            ex.getResponseHeaders().add("Location", foreign + "/out");
            ex.sendResponseHeaders(302, -1);
        });

        assertThat(client.download(base + "/detail").status()).isEqualTo("BAD_URL");
        assertThat(hits).hasValue(0);
    }

    @Test
    @DisplayName("허용 호스트 안의 리다이렉트는 따라간다")
    void redirectWithinHostIsFollowed() throws IOException {
        server.createContext("/detail", ex -> {
            ex.getResponseHeaders().add("Location", "/detail2");
            ex.sendResponseHeaders(302, -1);
        });
        serve("/detail2", 200, null, utf8("<a href=\"" + base + "/att?no=1\">공고문</a>"));
        serve("/att", 200, null, pdf("MOVED BODY"));

        NoticeDocClient.Download d = client.download(base + "/detail");
        assertThat(d.status()).isEqualTo("OK");
        assertThat(d.text()).contains("MOVED BODY");
    }

    @Test
    @DisplayName("상한을 넘는 첨부 하나는 건너뛰고 다음 첨부 PDF 를 읽는다")
    void oversizedAttachmentIsSkipped() throws IOException {
        byte[] small = pdf("NOTICE BODY");
        ReflectionTestUtils.setField(client, "maxBytes", small.length + 100);
        byte[] big = new byte[small.length + 500];
        System.arraycopy(utf8("%PDF"), 0, big, 0, 4);
        serve("/detail", 200, null, utf8("<a href=\"" + base + "/att?sn=1\">a</a><a href=\"" + base + "/att?sn=2\">b</a>"));
        server.createContext("/att", ex -> {
            byte[] body = ex.getRequestURI().getQuery().contains("sn=1") ? big : small;
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });

        NoticeDocClient.Download d = client.download(base + "/detail");
        assertThat(d.status()).isEqualTo("OK");
        assertThat(d.text()).contains("NOTICE BODY");
    }

    @Test
    @DisplayName("상한을 넘는 첨부는 PDFBox 에 넘기지 않고 TOO_BIG")
    void oversizedAttachmentIsTooBig() throws IOException {
        ReflectionTestUtils.setField(client, "maxBytes", 200);
        serve("/detail", 200, null, utf8("<a href=\"" + base + "/att?no=1\">공고문</a>"));
        serve("/att", 200, null, pdf("BODY"));

        assertThat(client.download(base + "/detail").status()).isEqualTo("TOO_BIG");
    }

    @Test
    @DisplayName("첨부 파일명 퍼센트 인코딩이 깨져도 PDF 는 이름 없이 받는다")
    void badFilenameStillReadsPdf() throws IOException {
        serve("/detail", 200, null, utf8("<a href=\"" + base + "/att?no=1\">공고문</a>"));
        serve("/att", 200, "attachment; filename*=UTF-8''%E0%A4%A", pdf("BODY"));

        NoticeDocClient.Download d = client.download(base + "/detail");

        assertThat(d.status()).isEqualTo("OK");
        assertThat(d.text()).contains("BODY");
    }

    @Test
    @DisplayName("호스트 대소문자는 가리지 않는다")
    void hostIsCaseInsensitive() {
        serve("/detail", 200, null, utf8("<html>첨부 없음</html>"));
        ReflectionTestUtils.setField(client, "allowedHost", Pattern.compile("localhost"));

        assertThat(client.download(base.replace("127.0.0.1", "LOCALHOST") + "/detail").status()).isEqualTo("NO_LINK");
    }

    @Test
    @DisplayName("첨부가 둘이면 파일명에 「모집공고」 가 든 PDF 를 고른다(&amp; 는 & 로)")
    void picksNoticePdf() throws IOException {
        String html = "<a href=\"" + base + "/att?no=1&amp;sn=1\">안내</a><a href='" + base + "/att?no=1&amp;sn=2'>공고문</a>";
        serve("/detail", 200, null, utf8(html));
        server.createContext("/att", ex -> {
            boolean notice = ex.getRequestURI().getQuery().contains("sn=2");
            byte[] body = pdf(notice ? "NOTICE BODY" : "GUIDE BODY");
            ex.getResponseHeaders().add("Content-Disposition", notice
                    ? "attachment; filename*=UTF-8''%EC%9E%85%EC%A3%BC%EC%9E%90%EB%AA%A8%EC%A7%91%EA%B3%B5%EA%B3%A0%EB%AC%B8.pdf"
                    : "attachment; filename=\"guide.pdf\"");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });

        NoticeDocClient.Download d = client.download(base + "/detail");

        assertThat(d.status()).isEqualTo("OK");
        assertThat(d.text()).contains("NOTICE BODY");
        assertThat(client.fetch("APT", base + "/detail").status()).isEqualTo("OK");
    }

    @Test
    @DisplayName("상세 화면에 첨부 링크가 없으면 NO_LINK")
    void noLink() {
        serve("/detail", 200, null, utf8("<html>첨부 없음</html>"));
        assertThat(client.fetch("APT", base + "/detail")).isEqualTo(NoticeDoc.failed("NO_LINK"));
    }

    @Test
    @DisplayName("첨부가 PDF 가 아니면 NO_PDF")
    void notPdf() {
        serve("/detail", 200, null, utf8("<a href=\"" + base + "/att?no=1\">x</a>"));
        serve("/att", 200, "attachment; filename=\"a.hwp\"", utf8("HWP 문서"));
        assertThat(client.fetch("REMNDR", base + "/detail").status()).isEqualTo("NO_PDF");
    }

    @Test
    @DisplayName("상세 화면이 500 이면 HTTP_500")
    void detailError() {
        serve("/detail", 500, null, utf8("err"));
        assertThat(client.fetch("APT", base + "/detail").status()).isEqualTo("HTTP_500");
    }
}
