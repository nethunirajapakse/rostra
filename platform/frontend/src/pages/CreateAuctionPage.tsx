import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { useCreateAuction, useUploadImage } from "../api/auctions";
import { PhotoPicker } from "../components/PhotoPicker";
import { Button, ErrorNotice, TextArea, TextField } from "../components/ui";
import { errorText } from "../lib/api";
import { formatDateTime, toLocalInput } from "../lib/format";
import { useTitle } from "../lib/useTitle";
import { toast } from "../store/toasts";

const MINUTE = 60_000;
const DURATIONS = [
  { label: "5 minutes", ms: 5 * MINUTE },
  { label: "1 hour", ms: 60 * MINUTE },
  { label: "1 day", ms: 24 * 60 * MINUTE },
  { label: "3 days", ms: 3 * 24 * 60 * MINUTE },
  { label: "7 days", ms: 7 * 24 * 60 * MINUTE },
];

/** The next whole minute, plus an offset: a start time that is still in the future when the request lands. */
const startingIn = (minutes: number) => new Date(Math.ceil(Date.now() / MINUTE) * MINUTE + minutes * MINUTE);

export function CreateAuctionPage() {
  useTitle("Start an auction");
  const navigate = useNavigate();
  const create = useCreateAuction();
  const uploadImage = useUploadImage();
  const [photos, setPhotos] = useState<File[]>([]);
  const [uploading, setUploading] = useState<string | null>(null);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [startingPrice, setStartingPrice] = useState("");
  const [minIncrement, setMinIncrement] = useState("");
  const [startsAt, setStartsAt] = useState(() => toLocalInput(startingIn(1)));
  const [endsAt, setEndsAt] = useState(() => toLocalInput(new Date(startingIn(1).getTime() + 60 * MINUTE)));
  const [errors, setErrors] = useState<Record<string, string>>({});

  const setDuration = (ms: number) => setEndsAt(toLocalInput(new Date(new Date(startsAt).getTime() + ms)));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    const start = new Date(startsAt);
    const end = new Date(endsAt);
    if (!title.trim()) found.title = "Give the auction a title";
    if (!(Number(startingPrice) > 0)) found.startingPrice = "Enter a starting price above zero";
    if (!(Number(minIncrement) > 0)) found.minIncrement = "Enter a minimum raise above zero";
    if (Number.isNaN(start.getTime()) || start.getTime() <= Date.now()) found.startsAt = "The start time must be in the future";
    if (Number.isNaN(end.getTime()) || end.getTime() <= start.getTime()) found.endsAt = "The auction must close after it opens";
    setErrors(found);
    if (Object.keys(found).length) return;

    create.mutate(
      {
        title: title.trim(),
        description: description.trim(),
        startingPrice: Number(startingPrice),
        minIncrement: Number(minIncrement),
        startsAt: start.toISOString(),
        endsAt: end.toISOString(),
      },
      {
        onSuccess: async (auction) => {
          // The auction exists now. Photos go up one at a time; a failed photo must not lose the auction,
          // and the seller can add or replace photos on the auction page.
          let failed = 0;
          for (let i = 0; i < photos.length; i++) {
            setUploading(`Uploading photo ${i + 1} of ${photos.length}`);
            try {
              await uploadImage.mutateAsync({ auctionId: auction.id, file: photos[i] });
            } catch {
              failed++;
            }
          }
          setUploading(null);
          if (failed) toast.warn(`Auction created, but ${failed} photo${failed === 1 ? "" : "s"} could not be uploaded. Add them below.`);
          else toast.good(`Auction created. Bidding opens ${formatDateTime(auction.startsAt)}.`);
          navigate(`/lot/${auction.id}`);
        },
      },
    );
  };

  return (
    <div className="max-w-2xl">
      <h1 className="font-display text-5xl leading-[1.05]">Start an auction</h1>
      <p className="mt-3 text-lg text-quiet">Bidding opens and closes by itself at the times you choose.</p>

      <form onSubmit={submit} noValidate className="mt-8 space-y-6 rounded-ui border border-rule bg-slab p-6 sm:p-8">
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} error={errors.title} autoFocus />
        <TextArea
          label="Description"
          hint="Condition, what is included, how it will be handed over."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={5000}
        />

        <PhotoPicker files={photos} onChange={setPhotos} />

        <div className="grid gap-6 sm:grid-cols-2">
          <TextField
            label="Starting price"
            inputMode="decimal"
            value={startingPrice}
            onChange={(e) => setStartingPrice(e.target.value.replace(/[^0-9.]/g, ""))}
            error={errors.startingPrice}
          />
          <TextField
            label="Each bid must raise by at least"
            inputMode="decimal"
            value={minIncrement}
            onChange={(e) => setMinIncrement(e.target.value.replace(/[^0-9.]/g, ""))}
            error={errors.minIncrement}
          />
        </div>

        <div className="grid gap-6 sm:grid-cols-2">
          <TextField label="Opens" type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} error={errors.startsAt} />
          <TextField label="Closes" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} error={errors.endsAt} />
        </div>

        <div>
          <p className="mb-2 text-sm font-medium">Run it for</p>
          <div className="flex flex-wrap gap-2">
            {DURATIONS.map((d) => (
              <button
                type="button"
                key={d.label}
                onClick={() => setDuration(d.ms)}
                className="rounded-full border border-rule px-3.5 py-1.5 text-sm font-medium hover:border-verdigris hover:text-verdigris-deep"
              >
                {d.label}
              </button>
            ))}
          </div>
        </div>

        {create.isError ? <ErrorNotice>{errorText(create.error)}</ErrorNotice> : null}

        <div className="flex items-center gap-3 border-t border-rule pt-6">
          <Button type="submit" disabled={create.isPending || uploading !== null}>
            {uploading ?? (create.isPending ? "Creating" : "Create auction")}
          </Button>
          <Button type="button" variant="quiet" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
