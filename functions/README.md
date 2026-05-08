# X List Feed Worker

This backend uses the official X API v2 List Posts endpoint for:

- `scheduledScrapeXList`: runs every 10 minutes and upserts list posts into Firestore.
- `getXListPosts`: callable pagination endpoint used by the Android X tab.

## Auth

Create an X Developer app with access to the List Posts endpoint, then store its bearer token as a Firebase secret:

```sh
firebase functions:secrets:set X_BEARER_TOKEN
```

Browser-cookie auth from `python-twitter/scrape_x_list.py` is useful for local experiments, but it is not a durable production worker strategy. Cloud Functions instances are ephemeral, X browser sessions expire, and internal GraphQL/browser automation is brittle. A bearer token secret is the path that can run unattended.

## Deploy

```sh
npm --prefix functions install
npm --prefix functions run build
firebase deploy --only functions,firestore:rules,firestore:indexes
```

The scheduled worker is the only caller of the X API. The Android app, including pull-to-refresh, reads from the cached Firestore store through `getXListPosts`; it does not trigger an X API refresh.

Firestore client reads/writes are closed in `firestore.rules`; app access goes through the callable functions so the store remains reusable without exposing direct database writes.
