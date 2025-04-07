## 1. User Collections
```
{
  "_id": ObjectId,
  // MongoDB's default primary key
  "username": String,
  "email": String,
  "department": String,
  "functionalManager": String,
  "entityManager": String,
  "jobTitle": String,
  "status": String,
  // e.g., "active", "inactive"
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String,
  "lastModifiedDate": Date,
  "region": String,
  // e.g., "EMEA", "APAC", "NA"
  "adGroups": [
    String
  ],
  // Array of AD group names (e.g., ["AD_Users", "AD_Developers"])
  "roleIds": [
    ObjectId
  ]
  // Array of roleIds
  // ... other user-related fields ...
}
```

## 2. Role Collections
```
{
  "_id": ObjectId,
  "roleName": String, // e.g., "Global Admin", "Regional Admin", "Approver", "User"
  "type": String, // e.g., "global", "regional", "user"
  "isApprover": Boolean, // Indicates if the role has approval permissions
  "description": String,
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String,
  "lastModifiedDate": Date,
  "region": String, // e.g., "EMEA", "APAC", "NA"
  "resourceIds": [ObjectId], // Array of resourceIds
  "attributeConditionIds": [ObjectId] // Array of attributeConditionIds
  // ... other role-related fields ...
}
```

## 3. Resource Collections

```
{
  "_id": ObjectId,
  "name": String, // e.g., "User Management Page", "Product Catalog"
  "permission": String, // e.g., "GET /users/*", "POST /products", "xxPage.button.view"
  "type": String, // e.g., "page", "button", "api"
  "description": String,
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String,
  "lastModifiedDate": Date
  // ... other resource-related fields ...
}
```

## 4. Attribute Condition Collections
```
{
  "_id": ObjectId,
  "groupName": String, // AD group name (e.g., "AD_Approvers", "AD_Users")
  "roleId": ObjectId, // Reference to a document in the `roles` collection
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String,
  "lastModifiedDate": Date
  // ... other group-default-role-related fields ...
}

```

## 5. Attribute Condition Collections
```
{
  "_id": ObjectId,
  "attributeName": String, // e.g., "jobStatus", "date"
  "expression": String, // JSON string, e.g., "{ \"jobStatus\": \"completed\" }", "[{from: \"2025-01-01\", to: \"2025-01-02\"}, {from: \"2025-01-03\", to: \"2025-01-04\"}, {date: \"2025-01-05\"}]"
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String
  // ... other attribute-condition-related fields ...
}

```

## 6. Audit Log Collections
```
{
  "_id": ObjectId,
  "userId": ObjectId, // Reference to a document in the `users` collection
  "action": String, // e.g., "user_created", "role_assigned", "resource_accessed"
  "detail": json,
  "createdBy": String,
  "updatedBy": String,
  "createdDate": Date,
  "lastModifiedBy": String
  // ... other audit-log-related fields ...
}
```
