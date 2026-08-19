# Đăng nhập bằng Google

## 1. Chọn luồng nào, và vì sao

Có hai cách tích hợp Google. Project này dùng cách thứ nhất.

| | **ID Token verification** (đang dùng) | Authorization Code flow |
|---|---|---|
| Ai nói chuyện với Google | Frontend | Backend |
| Backend cần session | Không | Có (lưu state chống CSRF) |
| Cần cấu hình redirect URI | Không | Có |
| Cần `client-secret` | Không | Có |

Backend hiện tại chạy `SessionCreationPolicy.STATELESS` và trả JWT cho SPA. Authorization
Code flow cần session tạm để giữ `state`, tức phải phá vỡ tính stateless hoặc thêm store
riêng. ID token verification không cần gì thêm, nên khớp kiến trúc sẵn có hơn.

## 2. Luồng chạy

```
Người dùng bấm "Sign in with Google"
        │
        ▼
Google trả ID token (JWT, RS256) cho frontend
        │
        ▼  POST /api/auth/google  { "idToken": "eyJ..." }
Backend: GoogleIdTokenVerifier
        │   1. Chữ ký   — khớp public key lấy từ JWKS của Google
        │   2. exp/iat  — token còn hạn
        │   3. iss      — đúng accounts.google.com
        │   4. aud      — đúng client ID của ta          ← quan trọng nhất
        │   5. email_verified == true
        ▼
AuthServiceImpl.loginWithGoogle → tìm/liên kết/tạo tài khoản
        │
        ▼
Trả access token (JSON) + refresh token (cookie HttpOnly)
```

Từ bước cuối trở đi mọi thứ giống đăng nhập thường: frontend dùng `accessToken` cho header
`Authorization`, và gọi `POST /api/auth/refresh` khi token hết hạn. Không cần code riêng cho
người dùng Google.

### Vì sao phải kiểm tra `aud`

Đây là bước hay bị bỏ sót nhất và cũng nguy hiểm nhất. Một ID token do Google ký cho **ứng
dụng khác** vẫn có chữ ký hợp lệ, issuer hợp lệ, còn hạn — tức qua được bước 1–3. Nếu không
so `aud` với client ID của mình, chủ sở hữu bất kỳ app Google nào cũng có thể lấy token của
họ để đăng nhập vào hệ thống này dưới danh nghĩa người dùng khác.

### Vì sao phải kiểm tra `email_verified`

Ta dùng email để liên kết tài khoản Google với tài khoản đăng ký bằng mật khẩu. Nếu chấp
nhận email chưa xác minh, kẻ tấn công có thể tạo tài khoản Google Workspace trên domain
riêng, đặt email trùng với nạn nhân, rồi chiếm tài khoản đó.

## 3. Lấy Google Client ID

1. Vào [Google Cloud Console](https://console.cloud.google.com/) → tạo project (hoặc chọn project có sẵn).
2. **APIs & Services → OAuth consent screen**: chọn External, điền tên app và email hỗ trợ.
   Khi còn ở chế độ Testing, thêm email của các thành viên vào **Test users**, nếu không sẽ bị chặn.
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID**.
4. Application type: **Web application**.
5. **Authorized JavaScript origins**: thêm origin của frontend, ví dụ `http://localhost:5173`.
   Luồng này *không* cần Authorized redirect URIs.
6. Copy **Client ID** (dạng `xxx.apps.googleusercontent.com`). Không cần Client Secret.

## 4. Cấu hình backend

Đặt biến môi trường trước khi chạy app:

```powershell
# PowerShell
$env:GOOGLE_CLIENT_ID = "xxx.apps.googleusercontent.com"
./mvnw spring-boot:run
```

Các khoá trong `application.yaml`:

```yaml
app:
  oauth:
    google:
      client-id: ${GOOGLE_CLIENT_ID:}
      jwk-set-uri: ${GOOGLE_JWK_SET_URI:https://www.googleapis.com/oauth2/v3/certs}
      issuers: ${GOOGLE_ISSUERS:https://accounts.google.com,accounts.google.com}
```

`client-id` để trống thì app vẫn khởi động bình thường, chỉ riêng `POST /api/auth/google`
trả `503 GOOGLE_LOGIN_NOT_CONFIGURED`. Làm vậy để thành viên chưa xin OAuth client vẫn chạy
được các phần khác của project, thay vì sập toàn bộ context — và quan trọng hơn là **không**
âm thầm bỏ qua bước kiểm tra `aud` khi thiếu cấu hình.

Client ID không phải bí mật (nó xuất hiện trong JavaScript của frontend), nên đưa vào biến
môi trường là đủ; không cần cơ chế quản lý secret.

## 5. API

`POST /api/auth/google` — không cần `Authorization` header.

```json
{ "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..." }
```

Phản hồi `200`, giống hệt `/api/auth/login`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 7,
  "email": "nguoidung@gmail.com",
  "role": "USER"
}
```

Kèm header `Set-Cookie: refresh_token=...; HttpOnly; Path=/api/auth`.

| Mã | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | thiếu hoặc rỗng `idToken` |
| 401 | `INVALID_GOOGLE_TOKEN` | sai chữ ký / hết hạn / sai `iss` / sai `aud` / email chưa xác minh |
| 401 | `ACCOUNT_DISABLED` | tài khoản bị vô hiệu hoá |
| 503 | `GOOGLE_LOGIN_NOT_CONFIGURED` | server chưa đặt `GOOGLE_CLIENT_ID` |

## 6. Ba trường hợp tài khoản

`AuthServiceImpl.loginWithGoogle` xử lý theo thứ tự:

1. **Tìm theo `google_id`** → đã liên kết trước đó. Đăng nhập, đồng bộ lại `fullName` và
   `avatarUrl` vì người dùng có thể đã đổi bên Google.
2. **Không thấy, tìm theo `email`** → tài khoản này vốn đăng ký bằng mật khẩu. **Liên kết**
   `google_id` vào đó, giữ nguyên `fullName` người dùng tự đặt, chỉ bổ sung avatar nếu còn
   trống. Không báo lỗi trùng email — nếu báo lỗi, người dùng sẽ bị kẹt: đăng nhập Google
   không được mà cũng không hiểu vì sao. Liên kết an toàn vì Google đã xác minh email.
3. **Không thấy cả hai** → tạo tài khoản mới với `password_hash = NULL`, role `USER`.

Sau khi liên kết ở trường hợp 2, người dùng đăng nhập được bằng **cả hai** cách.

Ràng buộc `chk_user_accounts_login_method` trong DB yêu cầu `password_hash` hoặc `google_id`
phải khác NULL, nên tài khoản tạo ở trường hợp 3 vẫn hợp lệ.

## 7. Ví dụ frontend

Dùng Google Identity Services, không cần thêm thư viện nào khác.

```html
<script src="https://accounts.google.com/gsi/client" async></script>
<div id="g_id_onload"
     data-client_id="xxx.apps.googleusercontent.com"
     data-callback="onGoogleSignIn"></div>
<div class="g_id_signin" data-type="standard"></div>

<script>
async function onGoogleSignIn(response) {
  // response.credential chính là ID token
  const res = await fetch('http://localhost:8080/api/auth/google', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',            // bắt buộc, để nhận cookie refresh token
    body: JSON.stringify({ idToken: response.credential }),
  });

  if (!res.ok) {
    const err = await res.json();
    console.error(err.code, err.message);
    return;
  }

  const { accessToken } = await res.json();
  sessionStorage.setItem('accessToken', accessToken);
}
</script>
```

Hai điểm dễ sai:

- **`credentials: 'include'`** — thiếu dòng này thì cookie refresh token không được lưu, và
  người dùng bị đăng xuất ngay khi access token hết hạn (15 phút).
- **Origin của frontend** phải nằm trong cả `CORS_ALLOWED_ORIGINS` của backend và
  Authorized JavaScript origins bên Google Console.

## 8. Kiểm thử

`GoogleIdTokenVerifierTest` tự sinh cặp khoá RSA và phục vụ JWKS qua HTTP server nội bộ nên
chạy được khi offline, không gọi ra Google thật.

```powershell
./mvnw test -Dtest=GoogleIdTokenVerifierTest
```

Phần lớn test nhắm vào các token **phải bị từ chối** (sai audience, sai khoá ký, hết hạn,
sai issuer, email chưa xác minh), vì đó mới là phần bảo vệ hệ thống. Các test đường hợp lệ
giữ vai trò chứng minh nhóm test từ chối không "đậu giả" vì JWKS tải thất bại — nếu decoder
từ chối mọi thứ một cách vô điều kiện, chúng sẽ đỏ.
