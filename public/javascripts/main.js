// =====================================================================
// AJAX ornegi: gorev listesinde "tamamla / yeniden-ac" islemini SAYFA
// YENILENMEDEN yapar.
//
// Progressive enhancement: bu script yuklenmezse veya hata verirse, formlar
// normal POST ile (sayfa yenilenerek) yine calisir. Buradaki is, o normal
// submit'i yakalayip (preventDefault) yerine bir `fetch` (AJAX) cagrisi yapmak.
//
// Akis:
//   1. .js-toggle-form submit'i yakala, tarayicinin normal POST'unu engelle.
//   2. fetch ile JSON ucuna POST at (CSRF token'i header'da yolla).
//   3. Donen JSON'a gore satirin durumunu ve butonu yerinde guncelle.
//   4. Hata olursa kullaniciya mesaj goster.
// =====================================================================
(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    var forms = document.querySelectorAll(".js-toggle-form");
    if (forms.length === 0) return; // Bu sayfada gorev listesi yok -> hicbir sey yapma.

    // Play CSRF korumasi: token'i layout'taki <meta>'dan okuyup her POST'ta
    // "Csrf-Token" header'i ile yollariz (yoksa sunucu 403 doner).
    var meta = document.querySelector('meta[name="csrf-token"]');
    var csrfToken = meta ? meta.getAttribute("content") : "";

    var flash = document.getElementById("ajax-flash");

    forms.forEach(function (form) {
      form.addEventListener("submit", function (event) {
        event.preventDefault(); // Normal POST + tam sayfa yenilemeyi iptal et.

        var btn = form.querySelector(".js-toggle-btn");
        var completed = btn.dataset.completed === "true";
        // Mevcut duruma gore dogru JSON ucunu sec.
        var url = completed ? btn.dataset.reopenUrl : btn.dataset.completeUrl;

        btn.disabled = true; // Cift tiklamayi engelle.

        fetch(url, {
          method: "POST",
          headers: {
            "Csrf-Token": csrfToken,
            "Accept": "application/json"
          }
        })
          .then(function (response) {
            // Hem basari hem hata govdesi JSON; ikisini de okuyup ok bayragiyla tasiyalim.
            return response.json().then(function (body) {
              return { ok: response.ok, body: body };
            });
          })
          .then(function (result) {
            if (result.ok) {
              applyToggle(btn, result.body.isCompleted); // Butonu + rozeti cevir.
              showFlash(result.body.message, true);
            } else {
              // Domain hatasi (orn. gecmis tarihli gorev tamamlanamaz) -> sunucudan gelen mesaj.
              showFlash(result.body.error || genericError(), false);
            }
          })
          .catch(function () {
            // Ag / beklenmeyen hata.
            showFlash(genericError(), false);
          })
          .finally(function () {
            btn.disabled = false;
          });
      });
    });

    // Butonu ve ilgili satirin durum rozetini yeni duruma gore guncelle.
    function applyToggle(btn, isCompleted) {
      btn.dataset.completed = String(isCompleted);
      btn.textContent = isCompleted ? btn.dataset.labelReopen : btn.dataset.labelComplete;

      var status = document.querySelector(
        '.task-status[data-task-id="' + btn.dataset.taskId + '"]'
      );
      if (status) {
        status.textContent = isCompleted ? status.dataset.labelDone : status.dataset.labelPending;
        status.classList.toggle("done", isCompleted);
        status.classList.toggle("pending", !isCompleted);
      }
    }

    function showFlash(message, success) {
      if (!flash || !message) return;
      flash.textContent = message;
      flash.className = success ? "flash-success" : "flash-error";
      flash.hidden = false;
    }

    function genericError() {
      return (flash && flash.dataset.genericError) || "Error";
    }
  });
})();
