let tmpRenPath;
let privateMode = false;

function loadContextMenu() {
  $(".ctxMenu").npContextMenu({
    setEvents: "contextmenu",
    menuSelector: "#contextMenu",
    onMenuOptionSelected: function (invokedOn, selectedMenu) {
      let val = invokedOn[0].dataset.ctxmap;
      let act = selectedMenu.attr("npaction");
      if (act == "ctx_delItem") {
        let item = [];
        if (parent == "/") {
          item.push(parent + val);
        } else {
          item.push(parent + "/" + val);
        }
        del(JSON.stringify(item));
      } else if (act == "ctx_dwnldItem") {
        let item = [];
        if (parent == "/") {
          item.push(parent + val);
        } else {
          item.push(parent + "/" + val);
        }
        download_m(JSON.stringify(item));
      } else if (act == "ctx_renameItem") {
        $("#newFName").val(val);
        $("#fRenameModel").modal("show",{ backdrop: "static", keyboard: false });
        if (parent == "/") {
          tmpRenPath = parent + val;
        } else {
          tmpRenPath = parent + "/" + val;
        }
      }
    },
  });
}

function renameF() {
  if (!privateMode) {
    var p;
    if (parent == "/") {
      p = "/";
    } else {
      p = parent + "/";
    }
    var n = $("#newFName").val();
    if (n.length == 0) {
      alert("Provide new name...!!!");
    } else {
      $.get(
        "ShareX",
        {
          action: "renF",
          old_n: tmpRenPath,
          new_n: n,
          parent: p,
        },
        function (response) {
          reload();
          $("#fRenameModel").modal("hide");
          toast("Information", response.split(";")[0], 3000);
        }
      );
    }
  }
}

function createF() {
  if (!privateMode) {
    var p;
    if (parent == "/") {
      p = "/";
    } else {
      p = parent + "/";
    }
    var n = $("#newFolName").val();
    if (n.length == 0) {
      alert("Provide folder name...!!!");
    } else {
      $.get(
        "ShareX",
        {
          action: "newF",
          name: n,
          parent: p,
        },
        function (response) {
          reload();
          $("#fCreateModel").modal("hide");
          toast("Information", response.split(";")[0], 3000);
        }
      );
    }
  }
}

$(document).ready(function () {
  $('[name="moreOpts"]').click(function () {
    let act = this.id;
    if (act == "more_home") {
      goHome();
    } else if (act == "more_refresh") {
      reload();
    } else if (act == "delBtn") {
      del(checkJSON());
    } else if (act == "more_download") {
      download_m(checkJSON());
    } else if (act == "more_createFolder") {
      $("#newFolName").val("");
      $("#fCreateModel").modal("show",{ backdrop: "static", keyboard: false });
    }
  });

  $("#newFolName").keypress((e) => {
    var keycode = e.keyCode ? e.keyCode : e.which;
    if (keycode == "13") {
      createF();
    }
  });

  $("#newFName").keypress((e) => {
    var keycode = e.keyCode ? e.keyCode : e.which;
    if (keycode == "13") {
      renameF();
    }
  });

  $("#renMBtn").click(function () {
    renameF();
  });

  $("#newMfBtn").click(function () {
    createF();
  });
});
