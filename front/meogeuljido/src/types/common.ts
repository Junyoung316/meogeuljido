export interface ApiFieldError {
  field: string
  reason: string
}

// GlobalExceptionHandler.ErrorResponse와 1:1 대응되는 타입
export interface ApiErrorBody {
  code: string
  message: string
  timestamp: string
  fieldErrors?: ApiFieldError[]
}
