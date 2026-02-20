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

  // --- Notify Me form ---
  var form = document.getElementById('notify-form');
  if (form) {
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var emailInput = document.getElementById('notify-email');
      var btn = document.getElementById('notify-btn');
      var msg = document.getElementById('notify-message');
      var email = emailInput.value.trim();

      btn.disabled = true;
      btn.textContent = 'Sending...';
      msg.style.display = 'none';

      fetch('/api/notify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email }),
      })
        .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
        .then(function (result) {
          if (!result.ok) {
            showMessage(msg, result.data.error || 'Something went wrong.', 'error');
          } else if (result.data.status === 'already_subscribed') {
            showMessage(msg, "You're already on the list. We'll be in touch!", 'info');
          } else {
            showMessage(msg, "You're in! We'll email you when it's ready.", 'success');
            emailInput.value = '';
          }
        })
        .catch(function () {
          showMessage(msg, 'Network error. Please try again.', 'error');
        })
        .finally(function () {
          btn.disabled = false;
          btn.textContent = 'Notify Me';
        });
    });
  }

  function showMessage(el, text, type) {
    el.textContent = text;
    el.className = 'notify-message notify-' + type;
    el.style.display = 'block';
  }

});
