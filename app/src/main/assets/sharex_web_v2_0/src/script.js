let parent = "/";
let notyf = null;
let tab = 0;
let opened_img = "";
let openFlg = false;

$(document).ready(function () {
  $(function () {
    $("img").bind("contextmenu", function (e) {
      e.preventDefault();
    });
  });

  $(".container").on("dragenter", () => {
    getUploadLocation();
    $("#fileUploadModel").modal("show");
  });

  var cmanager = new CookieManager();
  var fsx_auth_token = "";
  if (cmanager.exist("fsx_auth_token")) {
    fsx_auth_token = cmanager.getCookie("fsx_auth_token");
  } else {
    fsx_auth_token = genUID();
    cmanager.setCookie("fsx_auth_token", fsx_auth_token);
  }

  function getAuth() {
    $("#main_bdy").css("display", "none");
    $("#init_wait").css("display", "");
    $.get(
      "ShareX",
      {
        action: "auth",
        device_id: fsx_auth_token,
      },
      function (res) {
        if (res == "true") {
          initApp();
        } else if (res == "denied") {
          $("#init_wait_text").html("Your request is denied for this session!");
          $(".logo_wrapper").css("display", "none");
          $(".static_logo").css("display", "");
        } else {
          getAuth();
        }
      }
    );
  }
  getAuth();
});

function initApp() {
  $("#init_wait").css("display", "none");
  $("#main_bdy").css("display", "");
  getPInfo();
  loadFiles(parent);
  loadApps();
  notifyChkBoxUI();
  $("#back_btn").click(function () {
    back();
  });

  function back() {
    if (parent != "/") {
      let bck = parent.split("/");
      bck.pop();
      bck = bck.join("/");
      parent = bck;
      loadFiles(bck);
    }
  }

  if (window.history && window.history.pushState) {
    window.history.pushState("forward", null, null);
    $(window).on("popstate", function () {
      back();
    });
  }

  $('[data-toggle="tab"]').click(function () {
    var mode = this.href;
    if (mode.endsWith("#fxApps")) {
      tab = 1;
    } else if (mode.endsWith("#fxFiles")) {
      tab = 0;
    }
    filter_list();
  });

  $("#search").on("input", function () {
    filter_list();
  });

  function filter_list() {
    var input, filter, table, tr, td, i, txtValue;
    input = $("#search").val();
    filter = input.toUpperCase();
    if (tab == 0) {
      table = document.getElementById("filesList");
    } else if (tab == 1) {
      table = document.getElementById("appsList");
    }
    tr = table.getElementsByTagName("tr");
    for (i = 0; i < tr.length; i++) {
      td = tr[i].getElementsByTagName("td")[1];
      if (td) {
        txtValue = td.textContent || td.innerText;
        if (txtValue.toUpperCase().indexOf(filter) > -1) {
          tr[i].style.display = "";
        } else {
          tr[i].style.display = "none";
        }
      }
    }
  }

  $("#info").focusin(function () {
    $.get(
      "ShareX",
      {
        action: "getInfo",
      },
      function (res) {
        res = res.split(";");
        $("#bat_p").html("Percentage: " + res[0] + "%");
        $("#bat_plg").html(res[1]);
        $("#bat_p").css("background-color", res[2]);
      }
    );
  });

  $("#fileUploadModelBtn").click(function () {
    getUploadLocation();
  });
}

function getUploadLocation() {
  $.get(
    "ShareX",
    {
      action: "getUploadLocation",
    },
    function (res) {
      $("#upload_location_lbl").html(`Upload Location: ${res}`);
    }
  );
}

function getPInfo() {
  $.get(
    "ShareX",
    {
      action: "getPrivateMode",
    },
    function (res) {
      if (res == "true") {
        privateMode = true;
      } else {
        privateMode = false;
      }
      if (privateMode) {
        $("#more_home").addClass("disabled");
        $("#more_createFolder").addClass("disabled");
        $("#delBtn").addClass("disabled");
        $(".ext-ctx").css("display", "none");
      }
    }
  );
}

function notifyChkBoxUI() {
  let b = false;
  let len = 0,
    x = 0;
  $('[name="fChkBoxes"]').each(function () {
    x++;
    if (this.checked) {
      b = true;
      len++;
    }
  });
  if (x == len) {
    document.getElementById("chkAll").checked = true;
  } else {
    document.getElementById("chkAll").checked = false;
  }
  changeSelcLabel(len);
  if (b) {
    $("#delBtn").css("display", "block");
    $("#more_download").css("display", "block");
  } else {
    $("#delBtn").css("display", "none");
    $("#more_download").css("display", "none");
  }
}

function loadApps() {
  $.get(
    "ShareX",
    {
      action: "listApps",
    },
    function (response) {
      $("#appsList").html("");
      $("#appsList").html(response);
    }
  );
}

function loadFiles(path) {
  openFlg = true;
  $("#filesList").html(`<tr>
    <td colspan="3" class="text-center">
      <span class="loader"></span>
    </td>
  </tr>`);
  $.get(
    "ShareX",
    {
      action: "listFiles",
      location: path,
    },
    function (response) {
      $("#filesList").html(response);
      updateAddrBar();
      loadContextMenu();
      changeSelcLabel(0);
      notifyChkBoxUI();
      document.getElementById("chkAll").checked = false;
      openFlg = false;
    }
  );
}

function updateAddrBar() {
  let t = parent;
  let f = "";
  if (t == "/") {
    t = "";
  }
  for (var i = 0; i < t.length; i++) {
    if (t[i] == "/") {
      f += " / ";
    } else {
      f += t[i];
    }
  }
  $("#addrBar").html("Storage" + f);
  $("#search").val("");
}

function openFile(path) {
  var form = document.createElement("form");
  var element1 = document.createElement("input");
  var element2 = document.createElement("input");
  form.method = "GET";
  form.action = "ShareX";
  form.target = "_blank";
  form.style = "visibility:hidden;position:absolute;";

  element1.value = "openFile";
  element1.name = "action";
  element1.style = "visibility:hidden;position:absolute;";

  element2.value = parent + "/" + path;
  element2.name = "location";
  element2.style = "visibility:hidden;position:absolute;";

  form.appendChild(element1);
  form.appendChild(element2);
  document.body.appendChild(form);
  form.submit();
}

function openFile_p(path) {
  var form = document.createElement("form");
  var element1 = document.createElement("input");
  var element2 = document.createElement("input");
  form.method = "GET";
  form.action = "ShareX";
  form.target = "_blank";
  form.style = "visibility:hidden;position:absolute;";

  element1.value = "openFile";
  element1.name = "action";
  element1.style = "visibility:hidden;position:absolute;";

  element2.value = decodeURI(path);
  element2.name = "location";
  element2.style = "visibility:hidden;position:absolute;";

  form.appendChild(element1);
  form.appendChild(element2);
  document.body.appendChild(form);
  form.submit();
}

function openFolder(path) {
  if (!openFlg) {
    loadFiles(parent + "/" + path);
    if (parent == "/") {
      parent = parent + path;
    } else {
      parent = parent + "/" + path;
    }
  }
}

function viewFile(path) {
  opened_img = parent + "/" + path;
  let img =
    "ShareX?action=viewImage&location=" + encodeURI(parent + "/" + path);
  $("#imgPreview").attr("src", img);
  $("#imgViewModel").modal("show");
}

function viewFile_p(path) {
  opened_img = path;
  let img = "ShareX?action=viewImage&location=" + encodeURI(path);
  $("#imgPreview").attr("src", img);
  $("#imgViewModel").modal("show");
}

function openImg() {
  download_m(`["${opened_img}"]`);
}

function goHome() {
  loadFiles("/");
  parent = "/";
}

function checkAll(b) {
  $('[name="fChkBoxes"]').each(function () {
    if (!b) {
      this.checked = b;
    } else {
      var row = "row_" + this.id;
      if ($('[id="' + row + '"]')[0].style.display != "none") {
        this.checked = b;
      }
    }
  });
  notifyChkBoxUI();
}

function checkJSON() {
  let checkItems = [];
  $('[name="fChkBoxes"]').each(function () {
    if (this.id != null) {
      if (this.checked) {
        if (!privateMode) {
          if (parent == "/") {
            checkItems.push(parent + this.id);
          } else {
            checkItems.push(parent + "/" + this.id);
          }
        } else {
          checkItems.push(this.id);
        }
      }
    }
  });
  return checkItems.length > 0 ? JSON.stringify(checkItems) : null;
}

function changeSelcLabel(count) {
  if (count > 0) {
    $("#selectLbl").html(
      `<span class="icn"><i class="fas fa-arrow-right"></i></span> <b>${count}</b> items selected`
    );
    $("#selectLbl").show();
    $("#btnGroupDrop1").addClass("focus_btn");
  } else {
    $("#selectLbl").hide();
    $("#selectLbl").html("");
    $("#btnGroupDrop1").removeClass("focus_btn");
  }
}

function del(items) {
  if (!privateMode) {
    let b = confirm("Are you sure to delete files?");
    if (b) {
      $("#delBtn").css("display", "none");
      toast("Deleting Files", "Please Wait...");
      $.get(
        "ShareX",
        {
          action: "delFiles",
          data: items,
        },
        function (response) {
          let obj = response.split(";");
          reload();
          toast("Information", obj[0], 3000);
        }
      );
    }
  }
}

function genFileName() {
  var today = new Date();
  var fname =
    today.getDate() +
    (today.getMonth() + 1) +
    today.getFullYear() +
    "_" +
    today.getHours() +
    today.getMinutes() +
    today.getSeconds();
  return fname;
}

function genUID() {
  return Math.round(new Date().getTime() / 100);
}

function download_m(data) {
  var form = document.createElement("form");
  var element1 = document.createElement("input");
  var element2 = document.createElement("input");
  var element3 = document.createElement("input");
  form.method = "GET";
  form.action = "ShareX";
  form.target = "_blank";
  form.style = "visibility:hidden;position:absolute;";
  element1.value = "downloadFiles";
  element1.name = "action";
  element1.style = "visibility:hidden;position:absolute;";
  element2.value = data;
  element2.name = "data";
  element2.style = "visibility:hidden;position:absolute;";
  element3.value = genFileName();
  element3.name = "filename";
  element3.style = "visibility:hidden;position:absolute;";
  form.appendChild(element1);
  form.appendChild(element2);
  form.appendChild(element3);
  document.body.appendChild(form);
  form.submit();
}

function getApp(pkg) {
  var form = document.createElement("form");
  var element1 = document.createElement("input");
  var element2 = document.createElement("input");
  form.method = "GET";
  form.action = "ShareX";
  form.target = "_blank";
  form.style = "visibility:hidden;position:absolute;";
  element1.value = "getApp";
  element1.name = "action";
  element1.style = "visibility:hidden;position:absolute;";
  element2.value = pkg;
  element2.name = "pkg";
  element2.style = "visibility:hidden;position:absolute;";

  form.appendChild(element1);
  form.appendChild(element2);
  document.body.appendChild(form);
  form.submit();
}

function reload() {
  loadFiles(parent);
}

function toast(heading, msg, hideAfter = 0) {
  if(notyf != null) {
    notyf.dismissAll();
  }
  notyf = new Notyf({
    duration: hideAfter,
    ripple: true,
    dismissible: true,
    types: [
      {
        type: 'info',
        className: "notyf_info",
        icon: {
          className: 'fa-solid fa-info',
          tagName: 'i',
          text: ''
        }
      }
    ]
  });
  notyf.open({
    type: 'info',
    message: `<b>${heading}</b><br>${msg}`
  });
}