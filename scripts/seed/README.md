# Demo data

`scripts/seed.sh` fills an empty Rostra through the public API, so everything it creates (users, auctions, bids,
notifications, watchlists, photos) is exactly what the app itself would produce.

```bash
docker compose --profile app up -d --build   # the whole stack
bash scripts/seed.sh
```

Sign in at http://localhost:3000 with `demo@rostra.dev` / `Rostra@Demo1`. Every account shares that password.

| File | Format (one record per line, `|` separated, `#` starts a comment) |
|---|---|
| `users.txt` | key, first name, last name, email |
| `auctions.txt` | key, seller key, title, description, starting price, minimum raise, opens in, closes in |
| `bids.txt` | auction key, bidder key, amount (placed top to bottom, so each must beat the one before) |
| `watchlist.txt` | user key, auction key |
| `images/<auction key>-N.jpg` | photos for that auction, uploaded in file-name order; the first is the cover |

Times such as `15s`, `45m`, `2d6h` are counted from the moment the script runs. Three short auctions finish about
two minutes after seeding, so the Ended tab and the "you won" notification are populated.

The pictures are illustrations drawn by `tools/generate-images.py` (needs Pillow), so no photographs are involved.
To start again: stop the app, `bash scripts/db-reset.sh`, start it, `bash scripts/seed.sh`.
