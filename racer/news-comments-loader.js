/* Load the optional announcement-comments UI after the core Racer scripts. */
(function () {
  var s = document.createElement('script');
  s.src = 'news-comments.js';
  s.defer = true;
  document.head.appendChild(s);
})();
