if (!window.interceptedCJ) {
  XMLHttpRequest.prototype.realOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.realSend = XMLHttpRequest.prototype.send;
}
window.interceptedCJ = true;
XMLHttpRequest.prototype.open = function () {
  this._url = arguments[1];
  return this.realOpen.apply(this, arguments);
};
XMLHttpRequest.prototype.send = function () {
  try {
    if (this._url.includes("/accounts/login/ajax/")) {
      console.log(
        "set_username:" + arguments[0].match(/username=([^&]*)/)[1]
      );
    }
  } catch (err) {
    console.log("Interception Error: ", err);
  }
  return this.realSend.apply(this, arguments);
};
