namespace * example

struct TUser {
    1: required string id;
    2: required string name;
}

exception TNotFoundException {}

service TMatchingService {
    TUser match(1: TUser me) throws (1: TNotFoundException notFound);
}