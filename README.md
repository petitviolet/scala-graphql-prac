# GraphQL server sample with sangria and Akka-HTTP

## run

```
sbt main/run
```

and then open `localhost:8080` in browser.

## query example
 
```
query MyQuery {
  user {
    all {
      id
    }
    by_email(email: "hello@example.com") {
      id
      name
      email
    }
  }
  todo {
    all {
      id
    }
    search(user_id: "hello") {
      id
      title
      description
      user {
        id
        name
      }
    }
  }
}
```

## mutation example

```
mutation UpdateTodo {
  todo {
    update(user_id: "hello", id: "todo-1", title: "updated", description: "updated-description") {
      title
      description
    }
  }
}
```
