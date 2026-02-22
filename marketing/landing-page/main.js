/* ParentApproved.tv — Landing Page JS
   FAQ accordion + beta banner dismiss + notify form. */

document.addEventListener('DOMContentLoaded', function () {

  // --- Beta banner dismiss ---
  var banner = document.querySelector('.beta-banner');
  var closeBtn = document.querySelector('.beta-banner-close');
  if (closeBtn && banner) {
    closeBtn.addEventListener('click', function () {
      banner.classList.add('hidden');
      try { sessionStorage.setItem('beta-banner-dismissed', '1'); } catch (e) {}
    });
    // Stay dismissed for this session
    try {
      if (sessionStorage.getItem('beta-banner-dismissed') === '1') {
        banner.classList.add('hidden');
      }
    } catch (e) {}
  }

  // --- FAQ accordion ---
  var faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach(function (item) {
    var question = item.querySelector('.faq-question');
    question.addEventListener('click', function () {
      var isOpen = item.classList.contains('open');
      // Close all others
      faqItems.forEach(function (other) { other.classList.remove('open'); });
      // Toggle this one
      if (!isOpen) { item.classList.add('open'); }
    });
  });

  // --- Smooth scroll for anchor links ---
  document.querySelectorAll('a[href^="#"]').forEach(function (link) {
    link.addEventListener('click', function (e) {
      var target = document.querySelector(this.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

  // --- Version info from version.json ---
  var versionEl = document.getElementById('version-info');
  if (versionEl) {
    fetch('/version.json')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        versionEl.textContent = 'v' + data.latest + ' — ' + data.releaseNotes;
      })
      .catch(function () {
        versionEl.textContent = '';
      });
  }

});
