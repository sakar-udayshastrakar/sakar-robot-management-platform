// Mirrors backend/src/main/java/com/sakarrobotics/cloud/common/web/ApiResponse.java
// and .../common/error/ApiErrorBody.java exactly — do not add fields the
// backend does not actually send.

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  requestId: string | null;
  timestamp: string;
  fieldErrors: ApiFieldError[] | null;
}

export interface ApiResponse<T> {
  data: T | null;
  error: ApiErrorBody | null;
}

// Mirrors Spring Data's Page<T> JSON shape (org.springframework.data.domain.Page).
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
