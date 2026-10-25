/** The same limits the auction service enforces; checking here only saves a round trip. */
export const MAX_IMAGES = 5;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

/** Returns a message when the file cannot be uploaded, otherwise null. */
export function imageProblem(file: File): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return `${file.name}: only JPEG, PNG or WebP images`;
  if (file.size > MAX_IMAGE_BYTES) return `${file.name}: larger than 5 MB`;
  return null;
}
