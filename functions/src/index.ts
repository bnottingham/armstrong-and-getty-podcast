import {initializeApp} from "firebase-admin/app";
import {FieldValue, Timestamp, getFirestore} from "firebase-admin/firestore";
import {logger} from "firebase-functions";
import {HttpsError, onCall} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import {onSchedule} from "firebase-functions/v2/scheduler";

initializeApp();

const db = getFirestore();

const REGION = "us-central1";
const LIST_ID = "2030705875623755954";
const LIST_URL = `https://x.com/i/lists/${LIST_ID}`;
const X_API_URL = `https://api.x.com/2/lists/${LIST_ID}/tweets`;
const POSTS_COLLECTION = "xListPosts";
const META_COLLECTION = "xListMeta";
const PAGE_LIMIT_DEFAULT = 25;
const PAGE_LIMIT_MAX = 50;
const X_PAGE_LIMIT = 100;
const SCRAPE_LOCK_MS = 9 * 60 * 1000;

const xBearerToken = defineSecret("X_BEARER_TOKEN");

type XApiUser = {
  id: string;
  name?: string;
  username?: string;
  profile_image_url?: string;
  verified?: boolean;
  verified_type?: string;
};

type XApiMedia = {
  media_key: string;
  type?: string;
  url?: string;
  preview_image_url?: string;
  width?: number;
  height?: number;
  variants?: Array<{bit_rate?: number; content_type?: string; url?: string}>;
};

type XApiTweet = {
  id: string;
  text?: string;
  author_id?: string;
  created_at?: string;
  lang?: string;
  public_metrics?: {
    retweet_count?: number;
    reply_count?: number;
    like_count?: number;
    quote_count?: number;
    bookmark_count?: number;
    impression_count?: number;
  };
  attachments?: {
    media_keys?: string[];
  };
  entities?: {
    urls?: Array<{
      url?: string;
      expanded_url?: string;
      display_url?: string;
      unwound_url?: string;
      title?: string;
      description?: string;
    }>;
  };
  referenced_tweets?: Array<{type?: string; id?: string}>;
};

type XApiResponse = {
  data?: XApiTweet[];
  includes?: {
    users?: XApiUser[];
    media?: XApiMedia[];
  };
  meta?: {
    result_count?: number;
    next_token?: string;
  };
  errors?: Array<{title?: string; detail?: string; status?: number}>;
};

type StoredPost = {
  id: string;
  listId: string;
  listUrl: string;
  text: string;
  tweetUrl: string;
  authorId: string | null;
  author: {
    id: string | null;
    name: string;
    username: string;
    profileImageUrl: string | null;
    verified: boolean;
    verifiedType: string | null;
  };
  createdAt: Timestamp;
  createdAtMs: number;
  lang: string | null;
  metrics: {
    reposts: number;
    replies: number;
    likes: number;
    quotes: number;
    bookmarks: number;
    impressions: number;
  };
  media: Array<{
    key: string;
    type: string;
    url: string | null;
    previewImageUrl: string | null;
    width: number | null;
    height: number | null;
  }>;
  urls: Array<{
    url: string | null;
    expandedUrl: string | null;
    displayUrl: string | null;
    title: string | null;
  }>;
  referencedTweets: Array<{type: string | null; id: string | null}>;
  fetchedAt: FieldValue;
  source: "x-api-v2";
};

type ScrapeResult = {
  fetched: number;
  upserted: number;
  newestPostId: string | null;
  newestCreatedAtMs: number | null;
  skipped: boolean;
  mode?: "backfill" | "latest";
  reason?: string;
};

function bearerToken(): string {
  const token = xBearerToken.value() || process.env.X_BEARER_TOKEN;
  if (!token) {
    throw new HttpsError(
      "failed-precondition",
      "Missing X_BEARER_TOKEN secret. Run: firebase functions:secrets:set X_BEARER_TOKEN"
    );
  }
  return token;
}

function clampLimit(raw: unknown): number {
  const parsed = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isFinite(parsed)) return PAGE_LIMIT_DEFAULT;
  return Math.max(1, Math.min(Math.floor(parsed), PAGE_LIMIT_MAX));
}

function numberOrZero(raw: unknown): number {
  return typeof raw === "number" && Number.isFinite(raw) ? raw : 0;
}

function bestVideoUrl(media: XApiMedia): string | null {
  const variants = media.variants ?? [];
  const mp4s = variants.filter((variant) => variant.content_type === "video/mp4" && variant.url);
  if (mp4s.length === 0) return null;
  return mp4s.sort((a, b) => (b.bit_rate ?? 0) - (a.bit_rate ?? 0))[0].url ?? null;
}

function normalizePost(
  tweet: XApiTweet,
  usersById: Map<string, XApiUser>,
  mediaByKey: Map<string, XApiMedia>
): StoredPost | null {
  if (!tweet.id || !tweet.created_at) return null;

  const createdAtMs = Date.parse(tweet.created_at);
  if (!Number.isFinite(createdAtMs)) return null;

  const author = tweet.author_id ? usersById.get(tweet.author_id) : undefined;
  const username = author?.username ?? "";
  const media = (tweet.attachments?.media_keys ?? [])
    .map((key) => mediaByKey.get(key))
    .filter((item): item is XApiMedia => Boolean(item))
    .map((item) => ({
      key: item.media_key,
      type: item.type ?? "unknown",
      url: item.url ?? bestVideoUrl(item),
      previewImageUrl: item.preview_image_url ?? item.url ?? null,
      width: item.width ?? null,
      height: item.height ?? null,
    }));

  const tweetUrl = username ?
    `https://x.com/${username}/status/${tweet.id}` :
    `https://x.com/i/web/status/${tweet.id}`;

  return {
    id: tweet.id,
    listId: LIST_ID,
    listUrl: LIST_URL,
    text: tweet.text ?? "",
    tweetUrl,
    authorId: tweet.author_id ?? null,
    author: {
      id: author?.id ?? tweet.author_id ?? null,
      name: author?.name ?? username,
      username,
      profileImageUrl: author?.profile_image_url ?? null,
      verified: author?.verified ?? false,
      verifiedType: author?.verified_type ?? null,
    },
    createdAt: Timestamp.fromMillis(createdAtMs),
    createdAtMs,
    lang: tweet.lang ?? null,
    metrics: {
      reposts: numberOrZero(tweet.public_metrics?.retweet_count),
      replies: numberOrZero(tweet.public_metrics?.reply_count),
      likes: numberOrZero(tweet.public_metrics?.like_count),
      quotes: numberOrZero(tweet.public_metrics?.quote_count),
      bookmarks: numberOrZero(tweet.public_metrics?.bookmark_count),
      impressions: numberOrZero(tweet.public_metrics?.impression_count),
    },
    media,
    urls: (tweet.entities?.urls ?? []).map((url) => ({
      url: url.url ?? null,
      expandedUrl: url.expanded_url ?? url.unwound_url ?? null,
      displayUrl: url.display_url ?? null,
      title: url.title ?? null,
    })),
    referencedTweets: (tweet.referenced_tweets ?? []).map((ref) => ({
      type: ref.type ?? null,
      id: ref.id ?? null,
    })),
    fetchedAt: FieldValue.serverTimestamp(),
    source: "x-api-v2",
  };
}

async function fetchXPage(paginationToken?: string): Promise<XApiResponse> {
  const url = new URL(X_API_URL);
  url.searchParams.set("max_results", String(X_PAGE_LIMIT));
  url.searchParams.set(
    "tweet.fields",
    [
      "attachments",
      "author_id",
      "created_at",
      "entities",
      "lang",
      "public_metrics",
      "referenced_tweets",
    ].join(",")
  );
  url.searchParams.set("expansions", "author_id,attachments.media_keys");
  url.searchParams.set("user.fields", "name,username,profile_image_url,verified,verified_type");
  url.searchParams.set("media.fields", "preview_image_url,type,url,variants,width,height");
  if (paginationToken) {
    url.searchParams.set("pagination_token", paginationToken);
  }

  const response = await fetch(url, {
    headers: {
      "Authorization": `Bearer ${bearerToken()}`,
      "User-Agent": "ag-podcast-firebase-worker/1.0",
    },
  });

  const body = await response.text();
  let parsed: XApiResponse;
  try {
    parsed = JSON.parse(body) as XApiResponse;
  } catch (error) {
    throw new HttpsError("internal", `X API returned non-JSON response (${response.status})`);
  }

  if (!response.ok) {
    const detail = parsed.errors?.map((item) => item.detail || item.title).filter(Boolean).join("; ");
    throw new HttpsError("internal", `X API request failed (${response.status}): ${detail || body}`);
  }

  return parsed;
}

async function fetchListPosts(fetchAllPages: boolean): Promise<StoredPost[]> {
  const postsById = new Map<string, StoredPost>();
  let nextToken: string | undefined;

  do {
    const page = await fetchXPage(nextToken);
    const usersById = new Map((page.includes?.users ?? []).map((user) => [user.id, user]));
    const mediaByKey = new Map((page.includes?.media ?? []).map((media) => [media.media_key, media]));

    for (const tweet of page.data ?? []) {
      const post = normalizePost(tweet, usersById, mediaByKey);
      if (post) postsById.set(post.id, post);
    }

    nextToken = page.meta?.next_token;
  } while (fetchAllPages && nextToken);

  return [...postsById.values()].sort((a, b) => {
    if (b.createdAtMs !== a.createdAtMs) return b.createdAtMs - a.createdAtMs;
    return b.id.localeCompare(a.id);
  });
}

async function cacheHasPosts(): Promise<boolean> {
  const snap = await db.collection(POSTS_COLLECTION).limit(1).get();
  return !snap.empty;
}

async function acquireScrapeLock(reason: string): Promise<boolean> {
  const ref = db.collection(META_COLLECTION).doc(LIST_ID);
  const now = Date.now();
  return db.runTransaction(async (transaction) => {
    const snap = await transaction.get(ref);
    const lockUntilMs = snap.get("scrapeLockUntilMs") as number | undefined;
    if (lockUntilMs && lockUntilMs > now) {
      return false;
    }

    transaction.set(ref, {
      listId: LIST_ID,
      listUrl: LIST_URL,
      scrapeLockUntilMs: now + SCRAPE_LOCK_MS,
      lastScrapeStartedAt: FieldValue.serverTimestamp(),
      lastScrapeReason: reason,
    }, {merge: true});
    return true;
  });
}

async function releaseScrapeLock(): Promise<void> {
  await db.collection(META_COLLECTION).doc(LIST_ID).set({
    scrapeLockUntilMs: FieldValue.delete(),
  }, {merge: true});
}

async function writePosts(
  posts: StoredPost[],
  reason: string,
  mode: "backfill" | "latest"
): Promise<ScrapeResult> {
  let upserted = 0;

  for (let i = 0; i < posts.length; i += 400) {
    const batch = db.batch();
    for (const post of posts.slice(i, i + 400)) {
      const ref = db.collection(POSTS_COLLECTION).doc(post.id);
      batch.set(ref, post, {merge: true});
      upserted += 1;
    }
    await batch.commit();
  }

  const newest = posts[0] ?? null;
  await db.collection(META_COLLECTION).doc(LIST_ID).set({
    listId: LIST_ID,
    listUrl: LIST_URL,
    lastScrapedAt: FieldValue.serverTimestamp(),
    lastScrapeReason: reason,
    lastScrapeMode: mode,
    fetchedCount: posts.length,
    upsertedCount: upserted,
    newestPostId: newest?.id ?? null,
    newestCreatedAtMs: newest?.createdAtMs ?? null,
    ...(mode === "backfill" ? {lastBackfilledAt: FieldValue.serverTimestamp()} : {}),
  }, {merge: true});

  return {
    fetched: posts.length,
    upserted,
    newestPostId: newest?.id ?? null,
    newestCreatedAtMs: newest?.createdAtMs ?? null,
    skipped: false,
    mode,
  };
}

async function scrapeAndStore(reason: string, fetchAllPages: boolean): Promise<ScrapeResult> {
  const mode = fetchAllPages ? "backfill" : "latest";
  const locked = await acquireScrapeLock(reason);
  if (!locked) {
    logger.info("Skipping X list scrape because another scrape is running", {reason});
    return {fetched: 0, upserted: 0, newestPostId: null, newestCreatedAtMs: null, skipped: true, reason: "locked", mode};
  }

  try {
    const posts = await fetchListPosts(fetchAllPages);
    const result = await writePosts(posts, reason, mode);
    logger.info("X list scrape complete", result);
    return result;
  } finally {
    await releaseScrapeLock();
  }
}

function serializePost(data: FirebaseFirestore.DocumentData) {
  return {
    id: data.id,
    tweetId: data.id,
    text: data.text ?? "",
    tweetUrl: data.tweetUrl ?? `https://x.com/i/web/status/${data.id}`,
    timestampMs: data.createdAtMs ?? 0,
    createdAtIso: data.createdAt instanceof Timestamp ?
      data.createdAt.toDate().toISOString() :
      new Date(data.createdAtMs ?? 0).toISOString(),
    author: data.author ?? null,
    metrics: data.metrics ?? null,
    media: data.media ?? [],
    urls: data.urls ?? [],
    referencedTweets: data.referencedTweets ?? [],
  };
}

export const scheduledScrapeXList = onSchedule({
  region: REGION,
  schedule: "*/10 * * * *",
  timeZone: "UTC",
  timeoutSeconds: 300,
  memory: "512MiB",
  secrets: [xBearerToken],
}, async () => {
  const shouldBackfill = !(await cacheHasPosts());
  await scrapeAndStore("scheduled", shouldBackfill);
});

export const getXListPosts = onCall({
  region: REGION,
  invoker: "public",
}, async (request) => {
  const limit = clampLimit(request.data?.limit);
  const cursor = request.data?.cursor as {createdAtMs?: unknown; id?: unknown} | undefined;

  let query = db.collection(POSTS_COLLECTION)
    .orderBy("createdAtMs", "desc")
    .orderBy("id", "desc")
    .limit(limit + 1);

  if (cursor?.createdAtMs && cursor?.id) {
    query = query.startAfter(Number(cursor.createdAtMs), String(cursor.id));
  }

  const snap = await query.get();
  const docs = snap.docs.slice(0, limit);
  const hasMore = snap.docs.length > limit;
  const last = docs[docs.length - 1];
  const lastData = last?.data();

  return {
    listId: LIST_ID,
    listUrl: LIST_URL,
    posts: docs.map((doc) => serializePost(doc.data())),
    nextCursor: hasMore && lastData ? {
      createdAtMs: lastData.createdAtMs ?? 0,
      id: lastData.id ?? last.id,
    } : null,
    hasMore,
  };
});
