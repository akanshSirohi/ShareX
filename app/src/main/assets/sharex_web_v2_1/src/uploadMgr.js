let ajaxReq = null;
let uploadDisabled = false;
let old_files = [];
let upload_stack_names = [];
let elapsed_time_interval;
let current_uploaded_bytes = 0;
let total_bytes = 0;
let last_bytes = 0;

$(document).ready(function () {
  let isFSelected = false;
  $("#uploadFile").on("change", function () {
    var len = $("#uploadFile")[0].files.length;
    upload_stack_names = [];
    old_files = $("#uploadFile")[0].files;
    var size = 0;
    if (len > 0) {
      isFSelected = true;
      var lbl = "";
      for (var i = 0; i < len; i++) {
        let file = $("#uploadFile")[0].files[i];
        upload_stack_names.push(file.name);
        lbl +=
          '<span class="badge bg-info">' +
          (i + 1) +
          ". " +
          textEllipses(file.name) +
          "</span>";
        size += eval(file.size);
      }
      $("#fUpLbl").html(lbl);
      $("#fCount").html(
        "Files Selected: " + len + "<br>" + "Total Size: " + humanFileSize(size)
      );
    }
  });

  $("#closeFUM").click(function () {
    old_files = [];
  });

  $(".inp-file").on("mouseover", function () {
    this.style.borderColor = "#CC0000";
  });

  $(".inp-file").on("mouseleave", function () {
    this.style.borderColor = "rgb(42,159,214)";
  });

  function progress(e) {
    if (e.lengthComputable) {
      var max = e.total;
      var current = e.loaded;
      current_uploaded_bytes = current;
      total_bytes = max;
      var percentage = ((current * 100) / max).toFixed(2);
      $("#upload_pBar").css("width", percentage + "%");
      $("#upload_pBar_lbl").html(percentage + "%");
      $("#upload_pBar_lbl2").html("Transferred: " + humanFileSize(current));
      if (percentage >= 100) {
        $("#upload_pBar_lbl").html("Writing Files...");
      }
    }
  }

  $("#c_upload").click(function () {
    if (ajaxReq != null) {
      ajaxReq.abort();
      alert("Upload Cancelled!");
      uploadEnd();
    }
  });

  $("#fileUploadBtn").click(function () {
    if (isFSelected) {
      var formData = new FormData();
      var size = 0;
      for (var i = 0; i < $("#uploadFile")[0].files.length; i++) {
        let file = $("#uploadFile")[0].files[i];
        if (file.size == 0) {
          continue;
        }
        size += file.size;
        formData.append("file_" + i, file);
        formData.append("name_" + i, file.name);
      }
      ajaxReq = $.ajax({
        url: "ShareX/uploadFile",
        type: "POST",
        data: formData,
        processData: false,
        contentType: false,
        success: function (data) {
          data =
            data +
            `<br>${humanFileSize(size)} transferred in ${humanTime(
              new Date().getTime() - $("#upload_pBar_lbl_time").data("start")
            )}`;
          toast("Information", data);
          uploadEnd();
        },
        xhr: function () {
          var myXhr = $.ajaxSettings.xhr();
          if (myXhr.upload) {
            myXhr.upload.addEventListener("progress", progress, false);
          }
          return myXhr;
        },
        beforeSend: function () {
          initUpload();
        },
      });
    }
  });

  function initUpload() {
    $("#upload_pBar").css("width", "0%");
    $("#fileUploadBtn").prop("disabled", true);
    $("#uploadFile").prop("disabled", true);
    $("#closeFUM").prop("disabled", true);
    $("[for=uploadFile]").removeClass("btn-primary").addClass("btn-secondary");
    $("#upload_pBar_container").show();
    $("#c_upload").show();
    toast("Uploading Files", "Please Wait...", 6000);
    uploadDisabled = true;
    $("#upload_pBar_lbl_time").data("start", new Date().getTime());
    elapsed_time_interval = setInterval(() => {
      $("#upload_pBar_lbl_time").html(
        "Elapsed Time: " +
          elapsedTime(
            new Date().getTime() - $("#upload_pBar_lbl_time").data("start")
          )
      );
      $("#upload_pBar_lbl_time2").html(
        "Estimated Time: " +
          estimatedTime(
            new Date().getTime() - $("#upload_pBar_lbl_time").data("start"),
            current_uploaded_bytes,
            total_bytes
          )
      );
      let bytes_in_second = current_uploaded_bytes - last_bytes;
      let speed = getReadableSpeed(bytes_in_second);
      $("#up_speed").html("Transfer Speed: " + speed);
      last_bytes = current_uploaded_bytes;
    }, 1000);
  }

  function estimatedTime(elapsedTime, uploadedBytes, totalBytes) {
    var uploadSpeed = uploadedBytes / (elapsedTime / 1000);
    var ms = ((totalBytes - uploadedBytes) / uploadSpeed) * 1000;
    var ht = humanTime(ms);
    if (ht.endsWith("ms")) {
      return "0 s";
    } else {
      return humanTime(ms);
    }
  }

  function elapsedTime(elapsedTime) {
    var seconds = Math.floor(elapsedTime / 1000);
    var minutes = Math.floor(seconds / 60);
    var hours = Math.floor(minutes / 60);
    hours %= 24;
    minutes %= 60;
    seconds %= 60;

    if (hours > 0) {
      hours = hours.toString().padStart(2, "0");
      return `${hours}:${minutes}:${seconds}`;
    } else if (minutes > 0) {
      minutes = minutes.toString().padStart(2, "0");
      return `00:${minutes}:${seconds}`;
    } else {
      seconds = seconds.toString().padStart(2, "0");
      return `00:00:${seconds}`;
    }
  }

  function humanTime(elapsedTime) {
    var seconds = Math.floor(elapsedTime / 1000);
    var minutes = Math.floor(seconds / 60);
    var hours = Math.floor(minutes / 60);
    hours %= 24;
    minutes %= 60;
    seconds %= 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m ${seconds}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    } else if (seconds > 0) {
      return `${seconds}s`;
    } else {
      return `${elapsedTime.toFixed(2)}ms`;
    }
  }

  function uploadEnd() {
    reload();
    $("#fUpLbl").html("");
    $("#uploadFile").val("");
    $("#fCount").html("Files Selected: 0");
    $("#upload_pBar_container").hide();
    $("#uploadFile").prop("disabled", false);
    $("#fileUploadBtn").prop("disabled", false);
    $("#closeFUM").prop("disabled", false);
    $("[for=uploadFile]").removeClass("btn-secondary").addClass("btn-primary");
    $("#upload_pBar").css("width", "0%");
    $("#upload_pBar_lbl").html("");
    $("#c_upload").hide();
    $("#upload_pBar_lbl_time").html("Elapsed Time: 00:00:00");
    isFSelected = false;
    uploadDisabled = false;
    upload_stack_names = [];
    current_uploaded_bytes = 0;
    totalBytes = 0;
    clearInterval(elapsed_time_interval);
  }

  function humanFileSize(bytes) {
    var thresh = 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + " B";
    }
    var units = ["KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
    var u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + " " + units[u];
  }

  function getReadableSpeed(bytes) {
    var thresh = 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + " bps";
    }
    var units = ["KB/s", "MB/s", "GB/s"];
    var u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(2) + " " + units[u];
  }

  function textEllipses(text) {
    if (text.length > 20) {
      return text.substring(0, 19) + "...";
    }
    return text;
  }

  // DragDrop Functions Start
  let dropArea = document.getElementById("drop-area");
  dropArea.addEventListener("dragenter", preventDefaults, false);
  dropArea.addEventListener("dragleave", preventDefaults, false);
  dropArea.addEventListener("dragover", preventDefaults, false);
  dropArea.addEventListener("drop", preventDefaults, false);
  function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
  }

  ["dragenter", "dragover"].forEach((eventName) => {
    dropArea.addEventListener(eventName, highlight, false);
  });

  ["dragleave", "drop"].forEach((eventName) => {
    dropArea.addEventListener(eventName, unhighlight, false);
  });

  function highlight(e) {
    if (!uploadDisabled) {
      dropArea.classList.add("highlight");
    }
  }

  function unhighlight(e) {
    dropArea.classList.remove("highlight");
  }

  dropArea.addEventListener("drop", handleDrop, false);

  function handleDrop(e) {
    if (!uploadDisabled) {
      let files = e.dataTransfer.files;
      if (old_files.length > 0) {
        var dt = new DataTransfer();
        for (var i = 0; i < old_files.length; i++) {
          if (old_files[i].size > 0) {
            dt.items.add(old_files[i]);
          }
        }
        for (var i = 0; i < files.length; i++) {
          if (files[i].size > 0) {
            if (!upload_stack_names.includes(files[i].name)) {
              dt.items.add(files[i]);
            }
          }
        }
        document.getElementById("uploadFile").files = dt.files;
        old_files = dt.files;
      } else {
        document.getElementById("uploadFile").files = files;
        old_files = files;
        upload_stack_names = [];
      }

      var len = $("#uploadFile")[0].files.length;
      var len2 = 0;
      var size = 0;
      if (len > 0) {
        isFSelected = true;
        var lbl = "";
        for (var i = 0; i < len; i++) {
          let file = $("#uploadFile")[0].files[i];
          if (file.size == 0) {
            continue;
          }
          upload_stack_names.push(file.name);
          lbl +=
            '<span class="badge bg-info">' +
            (len2 + 1) +
            ". " +
            textEllipses(file.name) +
            "</span>";
          size += eval(file.size);
          len2++;
        }
        $("#fUpLbl").html(lbl);
        $("#fCount").html(
          "Files Selected: " +
            len2 +
            "<br>" +
            "Total Size: " +
            humanFileSize(size)
        );
      }
    }
  }
  //DragDrop Functions End
});
