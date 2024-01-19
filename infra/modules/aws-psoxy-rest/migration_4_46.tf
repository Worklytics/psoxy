# lambda_urls conditional from 0.4.46
moved {
  from = aws_lambda_function_url.lambda_url
  to   = aws_lambda_function_url.lambda_url[0]
}
