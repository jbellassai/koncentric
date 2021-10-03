/*
 * Copyright 2021 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

create type user_status as enum ('ENABLED', 'DISABLED');

create table users (
    id                  serial primary key,
    external_id         uuid not null unique,
    email               varchar(255) not null unique,
    first_name          varchar(100) not null,
    last_name           varchar(100) not null,
    status              user_status not null default 'ENABLED'
);

create table groups(
    id                  serial primary key,
    external_id         uuid not null unique,
    name                varchar(100) not null unique
);

create table groups_users(
    group_id            int not null,
    user_id             int not null,
    since               timestamp with time zone not null,

    constraint fk_group_id foreign key(group_id) references groups(id) on delete cascade,
    constraint fk_user_id foreign key(user_id) references users(id) on delete cascade,
    constraint group_membership unique (group_id, user_id)
);