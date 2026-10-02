# Privacy Policy for Looply

**Effective Date:** October 2, 2026  
**Last Updated:** October 2, 2026  

Looply ("the App", "we", "us", or "our"), developed by **Amurot**, is an offline-first Instagram Reel player and organizer for Android. We are committed to protecting your privacy and ensuring transparency.

---

## 1. Local-First Architecture & Data Ownership

Looply is built on a **local-first philosophy**:
- All downloaded videos, thumbnails, creator handles, and playback preferences are stored exclusively on your device.
- **No Compulsory Accounts**: You do not need to register, log in, or provide any personal information to use Looply.
- **Zero Cloud Tracking**: We do not operate remote servers to track, monitor, or profile your saved videos.
- **No Data Commercialization**: We never sell, rent, or monetize your video data, watch habits, or clipboard contents.

---

## 2. Information Processed on Your Device

### A. Saved Videos & Metadata
- **Video Files & Thumbnails**: Video streams (`.mp4`) and thumbnail previews are saved in your device's app-specific external storage (`context.getExternalFilesDir("videos")`). You can delete them at any time to immediately reclaim disk space.
- **Room Database**: Video titles, captions, creator handles, and favorite states are stored in an encrypted/private SQLite database on your device.

### B. Clipboard & Share Sheet Access
- When using the Quick Paste button or when sharing via the Android Share Sheet, Looply processes the shared link solely to verify that it is an Instagram video URL and extract the media.
- Clipboard contents are never uploaded or retained for any purpose beyond user-initiated downloading.

---

## 3. Network Interactions

- **Media Extraction**: When you save an Instagram Reel, Looply communicates directly with the media source to retrieve the video stream and public thumbnail. These requests are sent directly from your device without intermediary proxy servers.
- **No Third-Party Ad Trackers**: Looply contains zero third-party advertising SDKs or tracking pixels.

---

## 4. Contact Us

If you have questions regarding this Privacy Policy or your data on Looply, contact Amurot at:  
**Email:** `privacy@amurot.com`
