// The screenshot carousel: swipe or scroll it, or use the arrows, the dots or
// the arrow keys. Without this script the slides still scroll sideways.
document.addEventListener("DOMContentLoaded", function () {
  var carousel = document.querySelector(".carousel");
  if (!carousel) return;
  var track = carousel.querySelector(".slides");
  var slides = Array.prototype.slice.call(track.querySelectorAll(".slide"));
  var dots = carousel.querySelector(".dots");
  var current = 0;
  var still = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  // From one slide to the next, gap included.
  function step() {
    return slides.length > 1 ? slides[1].offsetLeft - slides[0].offsetLeft : track.clientWidth;
  }

  function show(index) {
    index = (index + slides.length) % slides.length;
    track.scrollTo({ left: index * step(), behavior: still ? "auto" : "smooth" });
  }

  slides.forEach(function (slide, index) {
    var dot = document.createElement("button");
    dot.type = "button";
    dot.setAttribute("aria-label", "Screenshot " + (index + 1) + ": " + slide.querySelector("figcaption").textContent);
    dot.addEventListener("click", function () {
      show(index);
    });
    dots.appendChild(dot);
  });

  function mark() {
    var nearest = Math.round(track.scrollLeft / step());
    current = Math.max(0, Math.min(slides.length - 1, nearest));
    Array.prototype.forEach.call(dots.children, function (dot, index) {
      dot.setAttribute("aria-current", index === current ? "true" : "false");
    });
  }

  track.addEventListener("scroll", function () {
    window.requestAnimationFrame(mark);
  });
  carousel.querySelector(".prev").addEventListener("click", function () {
    show(current - 1);
  });
  carousel.querySelector(".next").addEventListener("click", function () {
    show(current + 1);
  });
  track.addEventListener("keydown", function (event) {
    if (event.key === "ArrowLeft") show(current - 1);
    else if (event.key === "ArrowRight") show(current + 1);
    else return;
    event.preventDefault();
  });
  carousel.classList.add("ready");
  mark();
});
