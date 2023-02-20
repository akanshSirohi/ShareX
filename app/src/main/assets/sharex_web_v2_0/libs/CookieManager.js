function CookieManager() {
  this.setCookie = function (key, val) {
    cookie_string = "fsx_" + key + "=" + val + "; path=/; max-age=63072000";
    document.cookie = cookie_string;
  };
  this.getCookie = function (key) {
    var name = "fsx_" + key + "=";
    var ca = document.cookie.split(";");
    for (var i = 0; i < ca.length; i++) {
      var c = ca[i];
      while (c.charAt(0) == " ") {
        c = c.substring(1);
      }
      if (c.indexOf(name) == 0) {
        return c.substring(name.length, c.length);
      }
    }
    return "";
  };
  this.exist = function (key) {
    return this.getCookie(key).length > 0;
  };
  this.deleteCookie = function (key) {
    if (this.exist(key)) {
      cookie_string =
        "fsx_" + key + "=;expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
      document.cookie = cookie_string;
    }
  };
}
