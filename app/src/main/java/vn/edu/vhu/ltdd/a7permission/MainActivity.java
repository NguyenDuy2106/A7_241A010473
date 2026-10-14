package vn.edu.vhu.ltdd.a7permission;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // TODO: thay 2201234567 bằng MSSV của bạn
    private static final String TAG = "A7_241A010473";

    private static final String CHANNEL_ID = "a7_channel";
    private static final int NOTI_ID = 1001;

    private ImageView imgAnh;
    private TextView tvTrangThai;
    private String thongTinAnh = "";

    // ---------------------------------------------------------------
    // 1) Bộ xin quyền CAMERA.
    //    registerForActivityResult phải được gọi TRƯỚC khi Activity ở trạng thái
    //    STARTED, nên ta khai báo ở cấp field (chạy trong constructor).
    // ---------------------------------------------------------------
    private final ActivityResultLauncher<String> xinQuyenCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền CAMERA: " + duocCap);
                if (duocCap) {
                    moCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Trạng thái 2: từ chối nhưng còn hỏi lại được
                    hienGiaiThich();
                } else {
                    // Trạng thái 3: đã chọn "Không cho phép" nhiều lần → hệ thống không
                    // hiện hộp thoại nữa, chỉ còn cách vào Cài đặt
                    hienMoCaiDat();
                }
            });

    // 2) Bộ xin quyền POST_NOTIFICATIONS (chỉ cần từ Android 13 / API 33)
    private final ActivityResultLauncher<String> xinQuyenThongBao = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    guiThongBao();
                } else {
                    Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
                }
            });

    // 3) Bộ mở Camera lấy ảnh xem trước (bitmap thu nhỏ, không cần lưu file)
    private final ActivityResultLauncher<Void> chupAnh = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            (Bitmap bitmap) -> {
                if (bitmap != null) {
                    imgAnh.setImageBitmap(bitmap);
                    thongTinAnh = getString(R.string.status_photo,
                            bitmap.getWidth(), bitmap.getHeight());
                    capNhatTrangThai();
                } else {
                    Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    // 4) NC1: Bộ xin 2 quyền cùng lúc (Camera & Danh bạ)
    private final ActivityResultLauncher<String[]> xinNhieuQuyen = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> result) -> {
                StringBuilder sb = new StringBuilder("Kết quả xin quyền:\n");
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    String permission = entry.getKey();
                    boolean isGranted = Boolean.TRUE.equals(entry.getValue());
                    if (Manifest.permission.CAMERA.equals(permission)) {
                        sb.append("- Camera: ").append(isGranted ? "Đã cấp" : "Từ chối").append("\n");
                    } else if (Manifest.permission.READ_CONTACTS.equals(permission)) {
                        sb.append("- Danh bạ: ").append(isGranted ? "Đã cấp" : "Từ chối").append("\n");
                    }
                }
                Toast.makeText(this, sb.toString().trim(), Toast.LENGTH_LONG).show();
                capNhatTrangThai();
            });

    // 5) NC2: Bộ xin quyền đọc danh bạ READ_CONTACTS
    private final ActivityResultLauncher<String> xinQuyenDanhBa = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    doc5LienHeDauTien();
                } else {
                    Toast.makeText(this, "Bạn đã từ chối quyền đọc danh bạ!", Toast.LENGTH_SHORT).show();
                }
                capNhatTrangThai();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        imgAnh = findViewById(R.id.imgAnh);
        tvTrangThai = findViewById(R.id.tvTrangThai);
        Button btnChupAnh = findViewById(R.id.btnChupAnh);
        Button btnThongBao = findViewById(R.id.btnThongBao);
        Button btnNC1 = findViewById(R.id.btnNC1);
        Button btnNC2 = findViewById(R.id.btnNC2);
        Button btnCaiDat = findViewById(R.id.btnCaiDat);

        taoKenhThongBao();

        btnChupAnh.setOnClickListener(v -> kiemTraRoiChup());
        btnThongBao.setOnClickListener(v -> kiemTraRoiGuiThongBao());
        btnNC1.setOnClickListener(v -> xinCapHaiQuyenCungLuc());
        btnNC2.setOnClickListener(v -> kiemTraRoiDocDanhBa());
        btnCaiDat.setOnClickListener(v -> moCaiDatUngDung());
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatTrangThai();   // người dùng có thể vừa đổi quyền trong Cài đặt rồi quay lại
    }

    // =====================  LUỒNG XIN QUYỀN CAMERA  =====================

    /** Mẫu chuẩn 3 nhánh mà Google khuyến nghị. */
    private void kiemTraRoiChup() {
        int trangThai = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (trangThai == PackageManager.PERMISSION_GRANTED) {
            // Nhánh 1: đã có quyền → dùng ngay
            moCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Nhánh 2: đã từng từ chối → giải thích lý do trước khi hỏi lại
            hienGiaiThich();
        } else {
            // Nhánh 3: lần đầu (hoặc đã bị chặn vĩnh viễn) → gọi thẳng hộp thoại hệ thống
            xinQuyenCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void hienGiaiThich() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_camera)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenCamera.launch(Manifest.permission.CAMERA))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void hienMoCaiDat() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(R.string.blocked_camera)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moCamera() {
        chupAnh.launch(null);
    }

    /** Mở đúng trang thông tin ứng dụng của app này trong Cài đặt. */
    private void moCaiDatUngDung() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    // =====================  LUỒNG THÔNG BÁO  =====================

    private void kiemTraRoiGuiThongBao() {
        // Trước Android 13 không có quyền POST_NOTIFICATIONS → gửi được ngay
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            guiThongBao();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            guiThongBao();
        } else {
            xinQuyenThongBao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Từ Android 8 (API 26) mọi thông báo đều phải thuộc về một kênh. */
    private void taoKenhThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kenh = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            kenh.setDescription(getString(R.string.channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(kenh);
        }
    }

    private void guiThongBao() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.noti_title))
                .setContentText(getString(R.string.noti_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(this).notify(NOTI_ID, b.build());
        } catch (SecurityException e) {
            // Người dùng vừa tắt quyền ở Cài đặt trong lúc app đang chạy
            Log.w(TAG, "Thiếu quyền gửi thông báo", e);
        }
    }

    // ===================== NC1: XIN HAI QUYỀN CÙNG LÚC =====================

    private void xinCapHaiQuyenCungLuc() {
        xinNhieuQuyen.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS
        });
    }

    // ===================== NC2: ĐỌC 5 LIÊN HỆ ĐẦU TIÊN =====================

    private void kiemTraRoiDocDanhBa() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            doc5LienHeDauTien();
        } else {
            xinQuyenDanhBa.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void doc5LienHeDauTien() {
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String[] projection = new String[]{
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        };

        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(
                uri,
                projection,
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                StringBuilder sb = new StringBuilder();
                int count = 0;
                int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
                if (nameIndex < 0) {
                    nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                }

                do {
                    if (nameIndex >= 0) {
                        String name = cursor.getString(nameIndex);
                        count++;
                        sb.append(count).append(". ").append(name != null ? name : "Chưa đặt tên").append("\n");
                    }
                } while (cursor.moveToNext() && count < 5);

                new AlertDialog.Builder(this)
                        .setTitle("NC2: 5 liên hệ đầu tiên")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("Đóng", null)
                        .show();
            } else {
                Toast.makeText(this, "Không tìm thấy liên hệ nào trong danh bạ!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc danh bạ", e);
            Toast.makeText(this, "Không thể đọc danh bạ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // =====================  HIỂN THỊ TRẠNG THÁI  =====================

    private void capNhatTrangThai() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean thongBao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean danhBa = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;

        if (!camera) {
            thongTinAnh = "";
        }

        String s = getString(R.string.status_format,
                camera ? getString(R.string.granted) : getString(R.string.denied),
                thongBao ? getString(R.string.granted) : getString(R.string.denied),
                danhBa ? getString(R.string.granted) : getString(R.string.denied));

        if (thongTinAnh != null && !thongTinAnh.isEmpty()) {
            s += "\n" + thongTinAnh;
        }

        tvTrangThai.setText(s);
        Log.d(TAG, s.replace("\n", " | "));
    }
}
