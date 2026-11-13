package com.portfolio.cheongyak;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 청약홈 공고 상세 화면에서 입주자모집공고문 PDF 를 받아 텍스트로 바꾸고 정형 문구를 뽑는다.
 * 청약홈 웹 서버를 부르는 것이라 요청 사이를 1초 띄운다. 실패는 예외 대신 status 로 돌려준다 —
 * 공고문 한 건이 안 받아져도 수집 전체를 멈추지 않는다(실측 실패 약 8%, 대부분 첨부 링크 없음).
 */
@Slf4j
@Service
public class NoticeDocClient {

    /** 요청 사이 대기. 테스트는 0초로 바꾼다 */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** 고른 공고문의 텍스트. 실패면 text 가 null 이고 status 에 사유 */
    record Download(String status, String text) {
    }

    /** 받은 응답. 본문은 maxBytes 안이다 */
    private record Fetched(int status, HttpHeaders headers, byte[] body) {
    }

    /** 본문이 maxBytes 를 넘음 */
    private static final class TooBigException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(90);
    private static final long GAP_MILLIS = 1000;
    private static final int MAX_REDIRECTS = 3;
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Pattern FILENAME_STAR = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME_PLAIN = Pattern.compile("filename=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    // 상세 화면 HTML 안의 첨부 링크. 테스트가 가짜 서버 주소로 바꾼다
    private Pattern attachLink = Pattern.compile("https://static\\.applyhome\\.co\\.kr/ai/aia/getAtchmnfl\\.do\\?[^\"'\\s<>]+");
    private Sleeper sleeper = Thread::sleep;
    // 청약홈 밖으로는 요청하지 않는다. 리다이렉트는 직접 따라가며 매 주소를 본다. 테스트가 가짜 서버 주소로 바꾼다
    private Pattern allowedHost = Pattern.compile("(?:[a-z0-9-]+\\.)*applyhome\\.co\\.kr");
    // 공고문 PDF 는 수 MB 다. 이를 크게 넘는 응답은 메모리를 지키려고 읽지 않는다. 테스트가 줄인다
    private int maxBytes = 30 * 1024 * 1024;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

    public NoticeDoc fetch(String kind, String pblancUrl) {
        Download d = download(pblancUrl);
        if (d.text() == null) {
            return NoticeDoc.failed(d.status());
        }
        return NoticeDocExtractor.extract(kind, d.text());
    }

    /** 상세 HTML → 첨부 링크들 → 파일명에 「모집공고」 가 든 첫 PDF(없으면 첫 PDF) → 텍스트 */
    Download download(String pblancUrl) {
        if (pblancUrl == null || pblancUrl.isBlank()) {
            return new Download("NO_LINK", null);
        }
        try {
            Fetched page = get(pblancUrl);
            if (page.status() != 200) {
                return new Download("HTTP_" + page.status(), null);
            }
            List<String> links = links(new String(page.body(), StandardCharsets.UTF_8));
            if (links.isEmpty()) {
                return new Download("NO_LINK", null);
            }
            byte[] chosen = null;
            boolean tooBig = false;
            for (String link : links) {
                Fetched att;
                try {
                    att = get(link);
                } catch (TooBigException e) {
                    // 안내 책자 같은 큰 첨부 뒤에 공고문 PDF 가 올 수 있다
                    tooBig = true;
                    continue;
                }
                if (att.status() != 200 || !isPdf(att.body())) {
                    continue;
                }
                boolean notice = filename(att.headers()).contains("모집공고");
                if (chosen == null || notice) {
                    chosen = att.body();
                }
                if (notice) {
                    break;
                }
            }
            if (chosen == null) {
                return new Download(tooBig ? NoticeDoc.TOO_BIG : "NO_PDF", null);
            }
            try (PDDocument doc = Loader.loadPDF(chosen)) {
                return new Download(NoticeDoc.OK, new PDFTextStripper().getText(doc));
            } catch (IOException | RuntimeException e) {
                log.warn("공고문 PDF 를 못 읽었다 - {}", e.getClass().getSimpleName());
                return new Download("PARSE_ERR", null);
            }
        } catch (IllegalArgumentException e) {
            // URI.create 가 깨진 주소에서, checkHost 가 청약홈 밖 주소에서 던진다
            log.warn("청약홈 공고문 주소·헤더 거부 - {}", e.getClass().getSimpleName());
            return new Download(NoticeDoc.BAD_URL, null);
        } catch (TooBigException e) {
            log.warn("청약홈 공고문 응답이 {} 바이트를 넘어 버렸다", maxBytes);
            return new Download(NoticeDoc.TOO_BIG, null);
        } catch (IOException e) {
            log.warn("청약홈 공고문 요청 실패 - {}", e.getClass().getSimpleName());
            return new Download("HTTP_ERR", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Download("HTTP_ERR", null);
        }
    }

    /** 리다이렉트는 MAX_REDIRECTS 번까지 따라간다. 넘으면 3xx 상태를 그대로 돌려준다 */
    private Fetched get(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        for (int hop = 0;; hop++) {
            checkHost(uri);
            sleeper.sleep(GAP_MILLIS);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(READ_TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<InputStream> res = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                Optional<String> location = res.headers().firstValue("Location");
                if (res.statusCode() / 100 == 3 && location.isPresent() && hop < MAX_REDIRECTS) {
                    uri = uri.resolve(location.get());
                    continue;
                }
                return new Fetched(res.statusCode(), res.headers(), read(in));
            }
        }
    }

    private byte[] read(InputStream in) throws IOException {
        byte[] body = in.readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw new TooBigException();
        }
        return body;
    }

    /** 허용 호스트가 아니면 IllegalArgumentException */
    private void checkHost(URI uri) {
        String host = uri.getHost();
        // 호스트 이름은 대소문자를 가리지 않는다
        if (host == null || !allowedHost.matcher(host.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("허용하지 않는 호스트");
        }
    }

    private List<String> links(String html) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = attachLink.matcher(html);
        while (m.find()) {
            out.add(m.group().replace("&amp;", "&"));
        }
        return new ArrayList<>(out);
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    /** Content-Disposition 파일명. filename*=UTF-8''(퍼센트 인코딩) 과 filename="…"(UTF-8 원문) 두 꼴이 온다 */
    private static String filename(HttpHeaders headers) {
        String cd = headers.firstValue("Content-Disposition").orElse("");
        Matcher star = FILENAME_STAR.matcher(cd);
        if (star.find()) {
            try {
                return URLDecoder.decode(star.group(1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // 퍼센트 인코딩이 깨졌다. 이름만 못 읽은 것이라 filename="…" 이나 빈 이름으로 넘어가 PDF 는 그대로 쓴다
                log.debug("첨부 파일명 퍼센트 인코딩이 깨졌다");
            }
        }
        Matcher plain = FILENAME_PLAIN.matcher(cd);
        // 헤더는 ISO-8859-1 로 읽힌다. 원래 바이트로 되돌려 UTF-8 로 다시 읽는다
        return plain.find() ? new String(plain.group(1).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8) : "";
    }
}
