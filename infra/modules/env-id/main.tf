

locals {

  potential_word_delimiters = [" ", "-", "_"]

  delimiters_to_replace = setsubtract(local.potential_word_delimiters, var.supported_word_delimiters)

  delimiters_to_replace_regex = "/${join("|", local.delimiters_to_replace)}/"

  id_with_preferred_delimiters = length(local.delimiters_to_replace) == 0 ? var.environment_name : replace(var.environment_name, local.delimiters_to_replace_regex, var.preferred_word_delimiter)

  exceeds_max_length = var.max_length != null && length(local.id_with_preferred_delimiters) > coalesce(var.max_length, 50)

  # how many digits of sha to consider are sufficient to avoid collisions in env names
  sha1_short_significant_digits = 6

  sha1_short = substr(sha1(local.id_with_preferred_delimiters), 0, local.sha1_short_significant_digits)

  truncated_id = "${substr(local.id_with_preferred_delimiters, 0, coalesce(var.max_length, 50) - local.sha1_short_significant_digits)}${local.sha1_short}"

  id = local.exceeds_max_length ? local.truncated_id : local.id_with_preferred_delimiters

}
