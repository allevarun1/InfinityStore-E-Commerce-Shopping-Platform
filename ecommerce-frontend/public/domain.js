// One address for everyone: the old pages.dev link moves to the shop's own domain, so a shopper's sign-in
// (which the browser keeps per address) is never split between two sites.
if (location.hostname === 'rekha-shopping-mall.pages.dev') {
  location.replace('https://rekhashoppingmall.com' + location.pathname + location.search + location.hash);
}
