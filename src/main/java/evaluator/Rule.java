package evaluator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Rule {
  private static final Map<String, Set<String>> VALID_TYPES;
  private static final Set<String> VALID_GENERAL_NAMES;
  private static final Set<String> VALID_PRODUCT_NAMES;
  private static final Set<String> VALID_FORMATS;

  static {
    VALID_GENERAL_NAMES = ImmutableSet.of("publish_datetime");
    VALID_PRODUCT_NAMES = ImmutableSet.of("title", "description", "price", "low_price",
        "currency", "availability", "brand", "image_urls", "color", "size", "material",
        "fit", "gender", "category", "manufacturer", "additional_image_links", "itemCondition",
            "list_price", "free_shipping_limit", "min_shipping_length", "shipping_earliest_arrival_date",
            "shipping_latest_arrival_date", "shipping_geo", "shipping_method", "shipping_policy",
            "return_length", "return_policy", "dimension", "size_type", "size_system", "sale_label",
            "scarcity_label", "free_shipping_label", "free_return_label", "best_seller_label", "new_arrival_label",
            "avg_review_rating", "num_ratings", "review_link", "review_low_rating", "review_high_rating", "item_id",
            "item_set_id", "gtin", "upc", "mpn", "has_variants", "variant_skus", "promo_code", "image_link", "sale_price",
            "low_list_price", "high_list_price", "low_sale_price", "high_sale_price",
            "is_item", "is_item_set", "variant_availability", "installment_sale_price", "number_of_installments", 
	    "variant_urls", "variant_value_options", "variant_value_counts", "variant_num_dims", "variant_names", "variant_values", "variant_urls_base",
            "installment_frequency");
    VALID_TYPES = ImmutableMap.of(
        "general", VALID_GENERAL_NAMES,
        "product", VALID_PRODUCT_NAMES
    );
    VALID_FORMATS = ImmutableSet.of("TEXT", "LIST_TEXT");
  }

  @JsonProperty
  String name;

  @JsonProperty
  String xPath;

  @JsonProperty
  List<String> multipleXPaths = null;

  List<Evaluator> evaluators = null;

  @JsonProperty("output_format")
  String outputFormat;

  public void validateAndInitialize(XPathCompiler xPathCompiler, String type) {
    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(name),
        "Name must be non-empty!");

    boolean hasXPath = !Strings.isNullOrEmpty(xPath);
    boolean hasMultipleXPaths = multipleXPaths != null && multipleXPaths.size() > 0;
    Preconditions.checkArgument(hasXPath || hasMultipleXPaths, "Must either have xPath or multipleXPaths");
    Preconditions.checkArgument(!(hasXPath && hasMultipleXPaths), "Cannot have xPath and multipleXPaths at the same time");

    Preconditions.checkArgument(
        !Strings.isNullOrEmpty(outputFormat),
        "output_format must be non-empty!");
    Preconditions.checkArgument(
        VALID_TYPES.containsKey(type.toLowerCase()),
        "Type must be one of {" + String.join(", ", VALID_TYPES.keySet()) + "}"
    );
    Set<String> validNames = VALID_TYPES.get(type.toLowerCase());
    Preconditions.checkArgument(validNames.contains(name.toLowerCase()),
        "Name must be one of {" + String.join(", ", validNames) + "}");
    Preconditions.checkArgument(VALID_FORMATS.contains(outputFormat.toUpperCase()),
        "output_format must be one of {" + String.join(", ", VALID_FORMATS) + "}");


    List<String> xpath_list = multipleXPaths;
    if (hasXPath) {
      xpath_list = ImmutableList.of(xPath);
    }

    evaluators = new ArrayList<>();
    for (String xpath: xpath_list) {
      Evaluator evaluator;
      try {
        XPathSelector eval = xPathCompiler.compile(xpath).load();
        evaluator = new Evaluator(eval, xpath);
      } catch (SaxonApiException e) {
        throw new RuntimeException("Failed to compile xPath: " + xpath, e);
      }
      evaluators.add(evaluator);
    }
  }
}
