package com.vego.backend.user;

import com.vego.backend.common.ApiException;
import com.vego.backend.common.Tokens;
import com.vego.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/** Lưu ảnh đại diện trên ổ đĩa server (volume Docker), không dùng dịch vụ lưu trữ bên ngoài. */
@Component
public class AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Tên tệp do server tự đặt: &lt;userId&gt;-&lt;24 hex&gt;.jpg|png — chặn đọc tệp ngoài thư mục. */
    private static final Pattern SAFE_NAME = Pattern.compile("^\\d+-[0-9a-f]{24}\\.(jpg|png)$");

    private final Path dir;

    public AvatarStorage(AppProperties props) throws IOException {
        this.dir = Paths.get(props.uploadDir(), "avatars").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        log.info("Thư mục ảnh đại diện: {}", dir);
    }

    /** Lưu tệp, trả về tên tệp mới. Chỉ nhận JPG/PNG (kiểm tra theo nội dung, không tin đuôi tệp). */
    public String save(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.field("EMPTY_FILE", "file", "Vui lòng chọn ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Ảnh tối đa 5 MB");
        }
        String ext = detectExtension(file)
                .orElseThrow(() -> ApiException.field("UNSUPPORTED_IMAGE", "file", "Chỉ hỗ trợ ảnh JPG hoặc PNG"));
        String name = userId + "-" + Tokens.randomHex(12) + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được ảnh đại diện", e);
        }
        return name;
    }

    public void delete(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return;
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException e) {
            log.warn("Không xóa được ảnh cũ {}: {}", name, e.getMessage());
        }
    }

    public Optional<Resource> load(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return Optional.empty();
        Path path = dir.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(new PathResource(path)) : Optional.empty();
    }

    private static Optional<String> detectExtension(MultipartFile file) {
        byte[] head = new byte[8];
        try (InputStream in = file.getInputStream()) {
            int n = in.readNBytes(head, 0, head.length);
            if (n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return Optional.of("jpg");
            }
            if (n >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                return Optional.of("png");
            }
        } catch (IOException ignored) {
            // rơi xuống trả về rỗng
        }
        return Optional.empty();
    }
}
