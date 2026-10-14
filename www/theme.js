// Light or dark: the visitor's last choice, else their system's. Runs in the
// head so the page never flashes the other theme.
(function () {
  var root = document.documentElement;
  var stored = null;
  try {
    stored = localStorage.getItem("theme");
  } catch (e) {}
  var prefersLight = window.matchMedia && matchMedia("(prefers-color-scheme: light)").matches;
  root.dataset.theme = stored || (prefersLight ? "light" : "dark");

  document.addEventListener("DOMContentLoaded", function () {
    var button = document.querySelector("button.theme");
    if (!button) return;
    button.addEventListener("click", function () {
      root.dataset.theme = root.dataset.theme === "light" ? "dark" : "light";
      try {
        localStorage.setItem("theme", root.dataset.theme);
      } catch (e) {}
    });
  });
})();
