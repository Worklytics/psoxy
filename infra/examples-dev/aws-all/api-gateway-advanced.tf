# example of setting up a custom domain for an API Gateway V2 via composition with Psoxy
# this example is provided for informational purposes only, with no warranty; please refer to AWS
# and Terraform documentation for the most up-to-date information

# resource "aws_acm_certificate" "cert" {
#   domain_name       = "example.com"
#   validation_method = "DNS"
#
#   lifecycle {
#     create_before_destroy = true
#   }
# }
#
# resource "aws_apigatewayv2_domain_name" "example" {
#   domain_name = "ws-api.example.com"
#
#   domain_name_configuration {
#     certificate_arn = aws_acm_certificate.cert.arn
#     endpoint_type   = "REGIONAL"
#     security_policy = "TLS_1_2" # this is a 'min version'; 'TLS_1_2' allows TLS v1.2 or TLS v1.3 in practice; see https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-custom-domain-tls-version.html
#   }
# }
#
# # see https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/apigatewayv2_api_mapping
# resource "aws_apigatewayv2_api_mapping" "example" {
#   api_id      = module.psoxy.api_gateway_v2.id
#   domain_name = aws_apigatewayv2_domain_name.example.id
#   stage       = module.psoxy.api_gateway_v2_stage.id
# }
#
# resource "aws_route53_zone" "main" {
#   name = "example.com"
# }
#
# resource "aws_route53_record" "main" {
#   name    = aws_apigatewayv2_domain_name.example.domain_name
#   type    = "A"
#   zone_id = aws_route53_zone.main.zone_id
#
#   alias {
#     name                   = aws_apigatewayv2_domain_name.example.domain_name_configuration[0].target_domain_name
#     zone_id                = aws_apigatewayv2_domain_name.example.domain_name_configuration[0].hosted_zone_id
#     evaluate_target_health = false
#   }
# }
