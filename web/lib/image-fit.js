export function fitImage(width, height, availableWidth, availableHeight) {
  const scale = Math.min(1, Math.max(1, availableWidth) / width, Math.max(1, availableHeight) / height);
  return { width: width * scale, height: height * scale };
}
