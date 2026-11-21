/* Pen Holder order helpers.
   Always use the Pen Holder's own palette when creating order text;
   never fall back to the Clicker palette's generic "Warna khusus" label. */
(function () {
  const PEN_HOLDER_PALETTE = [
    ["Pink Muda", "#ffc7e8"], ["Pink Tua", "#e84b9b"],
    ["Merah Muda", "#ff9da8"], ["Merah Tua", "#c62828"],
    ["Ungu Muda", "#c9a7f5"], ["Ungu Tua", "#673ab7"],
    ["Biru Muda", "#8ecbff"], ["Biru Tua", "#2455b8"],
    ["Hijau Muda", "#9be7b0"], ["Hijau Tua", "#16834a"],
    ["Kuning Muda", "#ffe58a"], ["Kuning Tua", "#d49a00"],
    ["Oranye Muda", "#ffc18a"], ["Oranye Tua", "#d96a12"],
    ["Putih", "#ffffff"], ["Abu-abu", "#9ca3af"], ["Hitam", "#1d1d1f"]
  ];

  const colorName = hex => {
    const match = PEN_HOLDER_PALETTE.find(([, value]) =>
      value.toLowerCase() === String(hex || "").toLowerCase()
    );
    return match ? match[0] : "Warna tidak dikenal (" + String(hex || "") + ")";
  };

  const formatOrder = (order, index) => [
    (index + 1) + ". " + (order.name || "(nama belum diisi)"),
    "   Ukuran: " + (order.size || "M"),
    "   Dasar: " + (order.flat ? "Versi 1 — dasar datar" : "Versi 2 — mengikuti font"),
    "   Font: " + order.font,
    "   Warna dasar: " + colorName(order.base),
    "   Warna tulisan: " + colorName(order.text)
  ].join("\n");

  window.DSPenHolderOrderColors = { palette: PEN_HOLDER_PALETTE, colorName, formatOrder };
})();