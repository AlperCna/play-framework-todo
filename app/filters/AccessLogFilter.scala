package filters

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import akka.stream.Materializer
import play.api.Logging
import play.api.mvc.{Filter, RequestHeader, Result}

/**
 * Erisim logu + istek suresi olcen Play HTTP Filter'i.
 *
 * NE ISE YARAR: Play, GELEN HER istegi (controller'a varmadan once) bir filter
 * ZINCIRINDEN gecirir. Bu yuzden filter, "her istekte tekrar eden" kesisen
 * ilgiler (cross-cutting concerns) icin dogru yerdir: erisim logu, sure olcumu,
 * request-id, gzip, guvenlik basliklari... Bu is controller'lara dagitilmaz;
 * tek yerde, tum uclar icin otomatik calisir.
 *
 * AKIS (Filter trait'inin sozlesmesi):
 *   apply(next)(rh):
 *     - `rh`  : gelen istegin basligi (method, uri, headers...). Henuz govde
 *               (body) okunmadigi icin parametre `RequestHeader`'dir.
 *     - `next`: zincirdeki BIR SONRAKI adim (sonraki filter ya da action).
 *               Cagirinca `Future[Result]` doner — yani yaniti ASENKRON bekleriz.
 *   Boylece istegi "sarariz": `next(rh)` ONCESI olculen an = baslangic,
 *   donen `Future`'i `map`'leyince elimize gecen an = bitis.
 *
 * SURE NEDEN `System.nanoTime`, projedeki [[services.Clock]] DEGIL?
 *   `Clock` duvar-saati verir (`Instant` = takvim ani). Iki duvar-saati farkini
 *   "gecen sure" sanmak HATALIDIR: NTP duzeltmesi/yaz-kis saati saati ileri-geri
 *   atlatabilir, fark negatif bile cikabilir. Sure olcumu icin MONOTON saat
 *   gerekir; JVM'de bunun araci `System.nanoTime()`'dir. `Clock` ise domain'in
 *   "su an hangi gun/an" sorusu icindir (is kurallari) — burada o soru yok.
 *   Yani Clock'u sokmamak bilincli bir tercih, eksik degil.
 *
 * MATERIALIZER: `Filter` trait'i bir `Materializer` ister (govde akislarini
 *   isleyebilmek icin Akka Streams altyapisi). Guice'in sagladigini implicit
 *   olarak enjekte edip trait'in ihtiyacini karsilariz.
 *
 * KAYIT (registration): bu sinif kendiliginden devreye girmez; `conf/application.conf`
 *   icinde `play.filters.enabled += "filters.AccessLogFilter"` ile zincire eklenir.
 */
class AccessLogFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext)
    extends Filter
    with Logging {

  override def apply(
      next: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    // Monoton baslangic ani (takvim degil, sayac).
    val startNanos = System.nanoTime()

    // Istegi zincirin geri kalanina devret; yanit hazir olunca `map` ile islercik.
    next(requestHeader).map { result =>
      val elapsedMs = (System.nanoTime() - startNanos) / 1000000

      // Tek satir erisim logu: "GET /tasks -> 200 (12ms)".
      logger.info(
        s"${requestHeader.method} ${requestHeader.uri} -> ${result.header.status} (${elapsedMs}ms)"
      )

      // Yaniti DEGISTIRME ornegi: olculen sureyi bir response header'a koyariz
      // (tarayicidaki Network sekmesinden ya da curl -I ile gorulebilir).
      result.withHeaders("Request-Time-Ms" -> elapsedMs.toString)
    }
  }
}
