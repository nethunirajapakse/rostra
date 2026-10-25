import { useEffect, useMemo, useRef, useState, type DragEvent } from "react";
import { imageProblem, MAX_IMAGES } from "../lib/images";
import { PlusIcon } from "./icons";

/**
 * Chooses photos before an auction exists. Nothing is uploaded here: the parent keeps the files and sends
 * them once the auction has an id. The first photo is the cover.
 */
export function PhotoPicker({ files, onChange }: { files: File[]; onChange: (files: File[]) => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  const previews = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files]);
  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews]);

  const add = (incoming: FileList | File[]) => {
    const accepted: File[] = [];
    let message: string | null = null;
    for (const file of Array.from(incoming)) {
      const bad = imageProblem(file);
      if (bad) message = bad;
      else if (files.length + accepted.length >= MAX_IMAGES) message = `At most ${MAX_IMAGES} photos`;
      else accepted.push(file);
    }
    setProblem(message);
    if (accepted.length) onChange([...files, ...accepted]);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    add(e.dataTransfer.files);
  };

  const makeCover = (index: number) => onChange([files[index], ...files.filter((_, i) => i !== index)]);

  return (
    <div>
      <p className="mb-1.5 text-sm font-medium">Photos</p>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`rounded-ui border border-dashed p-4 ${dragging ? "border-verdigris bg-verdigris-tint" : "border-rule bg-marble/50"}`}
      >
        {files.length > 0 ? (
          <ul className="mb-4 grid grid-cols-3 gap-3 sm:grid-cols-5">
            {files.map((file, i) => (
              <li key={`${file.name}-${file.lastModified}-${i}`} className="relative">
                <img src={previews[i]} alt={file.name} className="aspect-square w-full rounded-ui border border-rule object-cover" />
                {i === 0 ? (
                  <span className="absolute left-1 top-1 rounded-[3px] bg-verdigris px-1.5 py-0.5 text-xs font-semibold text-white">Cover</span>
                ) : null}
                <div className="mt-1.5 flex flex-wrap justify-between gap-x-2 text-xs font-semibold whitespace-nowrap">
                  {i === 0 ? (
                    <span />
                  ) : (
                    <button type="button" onClick={() => makeCover(i)} className="text-verdigris hover:underline">
                      Make cover
                    </button>
                  )}
                  <button type="button" onClick={() => onChange(files.filter((_, j) => j !== i))} className="text-alert hover:underline">
                    Remove
                  </button>
                </div>
              </li>
            ))}
          </ul>
        ) : null}

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => input.current?.click()}
            disabled={files.length >= MAX_IMAGES}
            className="inline-flex items-center gap-2 rounded-ui border border-rule bg-slab px-3.5 py-2 text-sm font-semibold hover:border-quiet disabled:opacity-50"
          >
            <PlusIcon /> Add photos
          </button>
          <span className="text-sm text-quiet">
            {files.length}/{MAX_IMAGES}. JPEG, PNG or WebP, up to 5 MB each. You can also drop them here.
          </span>
        </div>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          hidden
          onChange={(e) => {
            if (e.target.files) add(e.target.files);
            e.target.value = ""; // choosing the same file again should still fire
          }}
        />
      </div>
      {problem ? <p className="mt-1.5 text-sm text-alert">{problem}</p> : null}
    </div>
  );
}
