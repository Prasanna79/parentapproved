// POST /api/notify — collect email signups in KV
export async function onRequestPost(context) {
  const { request, env } = context;

  // CORS headers for same-origin form submission
  const headers = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
  };

  try {
    const body = await request.json();
    const email = (body.email || '').trim().toLowerCase();

    if (!email || !isValidEmail(email)) {
      return new Response(JSON.stringify({ error: 'Invalid email address' }), {
        status: 400,
        headers,
      });
    }

    // Check for duplicate
    const existing = await env.NOTIFY_EMAILS.get(email);
    if (existing) {
      return new Response(JSON.stringify({ status: 'already_subscribed' }), {
        status: 200,
        headers,
      });
    }

    // Store email with metadata
    const entry = {
      email,
      timestamp: new Date().toISOString(),
      source: 'landing-page',
    };
    await env.NOTIFY_EMAILS.put(email, JSON.stringify(entry));

    // Track in a date-indexed list for the daily digest
    const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
    const dailyKey = `_daily:${today}`;
    const dailyList = JSON.parse((await env.NOTIFY_EMAILS.get(dailyKey)) || '[]');
    dailyList.push(email);
    await env.NOTIFY_EMAILS.put(dailyKey, JSON.stringify(dailyList));

    return new Response(JSON.stringify({ status: 'subscribed' }), {
      status: 200,
      headers,
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: 'Bad request' }), {
      status: 400,
      headers,
    });
  }
}

// Handle OPTIONS for CORS preflight
export async function onRequestOptions() {
  return new Response(null, {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    },
  });
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 254;
}
