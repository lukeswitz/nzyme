import {OPERATORS} from "./Filters";

export const queryParametersToFilters = (queryParam, fields) => {
  let f;
  try {
    if (queryParam) {
      f = JSON.parse(queryParam);
    } else {
      return null;
    }
  } catch (error) {
    console.error("Failed to parse filter URL parameter JSON.");
    console.error(error);
    return null;
  }

  if (!f || Object.keys(f).length === 0) {
    return null;
  }

  let filters = {};

  Object.keys(f).forEach(field => {
    let filterField = fields[field];

    if (filterField) {
      f[field].forEach(fieldConfig => {
        let fieldParams = {};

        fieldParams["name"] = filterField.title;
        fieldParams["field"] = fieldConfig["field"];
        fieldParams["operator"] = fieldConfig["operator"];
        fieldParams["sign"] = translateOperatorSign(fieldConfig["operator"]);
        fieldParams["value"] = fieldConfig["value"];

        if (filterField.value_transform) {
          fieldParams["transformed_value"] = filterField.value_transform(fieldConfig["value"]);
        } else {
          fieldParams["transformed_value"] = null;
        }

        if (!filters[field]) {
          filters[field] = [];
        }

        filters[field].push(fieldParams);
      });
    }
  });

  return filters;
}

export const filtersToQueryParameters = (f) => {
  if (!f) {
    return null;
  }

  let queryParam = {};

  Object.keys(f).forEach(field => {
    f[field].forEach(fieldConfig => {
      let fieldParams = {};

      fieldParams["field"] = fieldConfig["field"];
      fieldParams["operator"] = fieldConfig["operator"];
      fieldParams["value"] = fieldConfig["value"];

      if (!queryParam[field]) {
        queryParam[field] = [];
      }

      queryParam[field].push(fieldParams);
    });
  });

  return queryParam
}

const translateOperatorSign = (operator) => {
  let result = "";
  Object.keys(OPERATORS).forEach((operatorName) => {
    const op = OPERATORS[operatorName];
    if (op.name === operator) {
      result = op.sign;
    }
  });

  return result;
}