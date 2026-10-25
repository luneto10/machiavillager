# Machiavillager bridge protocol

- [1. Property `Machiavillager bridge protocol > oneOf > Hello`](#oneOf_i0)
  - [1.1. Property `Machiavillager bridge protocol > oneOf > Hello > v`](#oneOf_i0_v)
  - [1.2. Property `Machiavillager bridge protocol > oneOf > Hello > type`](#oneOf_i0_type)
  - [1.3. Property `Machiavillager bridge protocol > oneOf > Hello > mod_version`](#oneOf_i0_mod_version)
  - [1.4. Property `Machiavillager bridge protocol > oneOf > Hello > protocol`](#oneOf_i0_protocol)
- [2. Property `Machiavillager bridge protocol > oneOf > Welcome`](#oneOf_i1)
  - [2.1. Property `Machiavillager bridge protocol > oneOf > Welcome > v`](#oneOf_i1_v)
  - [2.2. Property `Machiavillager bridge protocol > oneOf > Welcome > type`](#oneOf_i1_type)
  - [2.3. Property `Machiavillager bridge protocol > oneOf > Welcome > protocol`](#oneOf_i1_protocol)
- [3. Property `Machiavillager bridge protocol > oneOf > SpeakAction`](#oneOf_i2)
  - [3.1. Property `Machiavillager bridge protocol > oneOf > SpeakAction > v`](#oneOf_i2_v)
  - [3.2. Property `Machiavillager bridge protocol > oneOf > SpeakAction > type`](#oneOf_i2_type)
  - [3.3. Property `Machiavillager bridge protocol > oneOf > SpeakAction > request_id`](#oneOf_i2_request_id)
  - [3.4. Property `Machiavillager bridge protocol > oneOf > SpeakAction > action`](#oneOf_i2_action)
  - [3.5. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args`](#oneOf_i2_args)
    - [3.5.1. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args > lines`](#oneOf_i2_args_lines)
      - [3.5.1.1. Machiavillager bridge protocol > oneOf > SpeakAction > args > lines > lines items](#oneOf_i2_args_lines_items)
    - [3.5.2. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args > to`](#oneOf_i2_args_to)
  - [3.6. Property `Machiavillager bridge protocol > oneOf > SpeakAction > timeout_ms`](#oneOf_i2_timeout_ms)
  - [3.7. Property `Machiavillager bridge protocol > oneOf > SpeakAction > conversation_id`](#oneOf_i2_conversation_id)
  - [3.8. Property `Machiavillager bridge protocol > oneOf > SpeakAction > in_reply_to`](#oneOf_i2_in_reply_to)
- [4. Property `Machiavillager bridge protocol > oneOf > ActionStatus`](#oneOf_i3)
  - [4.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted`](#oneOf_i3_oneOf_i0)
    - [4.1.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > v`](#oneOf_i3_oneOf_i0_v)
    - [4.1.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > type`](#oneOf_i3_oneOf_i0_type)
    - [4.1.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > request_id`](#oneOf_i3_oneOf_i0_request_id)
    - [4.1.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > status`](#oneOf_i3_oneOf_i0_status)
  - [4.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running`](#oneOf_i3_oneOf_i1)
    - [4.2.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > v`](#oneOf_i3_oneOf_i1_v)
    - [4.2.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > type`](#oneOf_i3_oneOf_i1_type)
    - [4.2.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > request_id`](#oneOf_i3_oneOf_i1_request_id)
    - [4.2.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > status`](#oneOf_i3_oneOf_i1_status)
  - [4.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded`](#oneOf_i3_oneOf_i2)
    - [4.3.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > v`](#oneOf_i3_oneOf_i2_v)
    - [4.3.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > type`](#oneOf_i3_oneOf_i2_type)
    - [4.3.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > request_id`](#oneOf_i3_oneOf_i2_request_id)
    - [4.3.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > status`](#oneOf_i3_oneOf_i2_status)
  - [4.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed`](#oneOf_i3_oneOf_i3)
    - [4.4.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > v`](#oneOf_i3_oneOf_i3_v)
    - [4.4.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > type`](#oneOf_i3_oneOf_i3_type)
    - [4.4.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > request_id`](#oneOf_i3_oneOf_i3_request_id)
    - [4.4.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > status`](#oneOf_i3_oneOf_i3_status)
    - [4.4.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > reason`](#oneOf_i3_oneOf_i3_reason)
  - [4.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected`](#oneOf_i3_oneOf_i4)
    - [4.5.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > v`](#oneOf_i3_oneOf_i4_v)
    - [4.5.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > type`](#oneOf_i3_oneOf_i4_type)
    - [4.5.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > request_id`](#oneOf_i3_oneOf_i4_request_id)
    - [4.5.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > status`](#oneOf_i3_oneOf_i4_status)
    - [4.5.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > reason`](#oneOf_i3_oneOf_i4_reason)
  - [4.6. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted`](#oneOf_i3_oneOf_i5)
    - [4.6.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > v`](#oneOf_i3_oneOf_i5_v)
    - [4.6.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > type`](#oneOf_i3_oneOf_i5_type)
    - [4.6.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > request_id`](#oneOf_i3_oneOf_i5_request_id)
    - [4.6.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > status`](#oneOf_i3_oneOf_i5_status)
    - [4.6.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > spoken`](#oneOf_i3_oneOf_i5_spoken)
    - [4.6.6. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > of`](#oneOf_i3_oneOf_i5_of)
- [5. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent`](#oneOf_i4)
  - [5.1. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > v`](#oneOf_i4_v)
  - [5.2. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > type`](#oneOf_i4_type)
  - [5.3. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > event_id`](#oneOf_i4_event_id)
  - [5.4. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > event`](#oneOf_i4_event)
  - [5.5. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > occurred_at`](#oneOf_i4_occurred_at)
  - [5.6. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data`](#oneOf_i4_data)
    - [5.6.1. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > player_uuid`](#oneOf_i4_data_player_uuid)
    - [5.6.2. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > player_name`](#oneOf_i4_data_player_name)
    - [5.6.3. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > text`](#oneOf_i4_data_text)
    - [5.6.4. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > addressed`](#oneOf_i4_data_addressed)
  - [5.7. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > conversation_id`](#oneOf_i4_conversation_id)
- [6. Property `Machiavillager bridge protocol > oneOf > Error`](#oneOf_i5)
  - [6.1. Property `Machiavillager bridge protocol > oneOf > Error > v`](#oneOf_i5_v)
  - [6.2. Property `Machiavillager bridge protocol > oneOf > Error > type`](#oneOf_i5_type)
  - [6.3. Property `Machiavillager bridge protocol > oneOf > Error > code`](#oneOf_i5_code)
  - [6.4. Property `Machiavillager bridge protocol > oneOf > Error > detail`](#oneOf_i5_detail)

**Title:** Machiavillager bridge protocol

|                           |                  |
| ------------------------- | ---------------- |
| **Type**                  | `combining`      |
| **Required**              | No               |
| **Additional properties** | Any type allowed |

**Description:** Messages exchanged between the Minecraft mod and the Python backend over one WebSocket. Every message is one JSON object with v and type. Design and wire rules: docs/decisions/0004-bridge-protocol.md.

| One of(Option)               |
| ---------------------------- |
| [Hello](#oneOf_i0)           |
| [Welcome](#oneOf_i1)         |
| [SpeakAction](#oneOf_i2)     |
| [ActionStatus](#oneOf_i3)    |
| [PlayerChatEvent](#oneOf_i4) |
| [Error](#oneOf_i5)           |

## <a name="oneOf_i0"></a>1. Property `Machiavillager bridge protocol > oneOf > Hello`

**Title:** Hello

|                           |               |
| ------------------------- | ------------- |
| **Type**                  | `object`      |
| **Required**              | No            |
| **Additional properties** | Not allowed   |
| **Defined in**            | #/$defs/Hello |

**Description:** Sent by the mod after connecting. Carries its mod version and the protocol it speaks.

| Property                                | Pattern | Type   | Deprecated | Definition | Title/Description |
| --------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i0_v )                     | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i0_type )               | No      | const  | No         | -          | Type              |
| + [mod_version](#oneOf_i0_mod_version ) | No      | string | No         | -          | Mod Version       |
| + [protocol](#oneOf_i0_protocol )       | No      | const  | No         | -          | Protocol          |

### <a name="oneOf_i0_v"></a>1.1. Property `Machiavillager bridge protocol > oneOf > Hello > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

### <a name="oneOf_i0_type"></a>1.2. Property `Machiavillager bridge protocol > oneOf > Hello > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "hello".

Specific value: `"hello"`

### <a name="oneOf_i0_mod_version"></a>1.3. Property `Machiavillager bridge protocol > oneOf > Hello > mod_version`

**Title:** Mod Version

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Version of the mod, read from its own metadata by the mod. Example: "0.1.0".

| Restrictions   |    |
| -------------- | -- |
| **Min length** | 1  |
| **Max length** | 32 |

### <a name="oneOf_i0_protocol"></a>1.4. Property `Machiavillager bridge protocol > oneOf > Hello > protocol`

**Title:** Protocol

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Protocol version the mod speaks. Only 1 is accepted today.

Specific value: `1`

## <a name="oneOf_i1"></a>2. Property `Machiavillager bridge protocol > oneOf > Welcome`

**Title:** Welcome

|                           |                 |
| ------------------------- | --------------- |
| **Type**                  | `object`        |
| **Required**              | No              |
| **Additional properties** | Not allowed     |
| **Defined in**            | #/$defs/Welcome |

**Description:** Sent by the backend in answer to hello when the protocol version is supported.

| Property                          | Pattern | Type  | Deprecated | Definition | Title/Description |
| --------------------------------- | ------- | ----- | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i1_v )               | No      | const | No         | -          | V                 |
| + [type](#oneOf_i1_type )         | No      | const | No         | -          | Type              |
| + [protocol](#oneOf_i1_protocol ) | No      | const | No         | -          | Protocol          |

### <a name="oneOf_i1_v"></a>2.1. Property `Machiavillager bridge protocol > oneOf > Welcome > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

### <a name="oneOf_i1_type"></a>2.2. Property `Machiavillager bridge protocol > oneOf > Welcome > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "welcome".

Specific value: `"welcome"`

### <a name="oneOf_i1_protocol"></a>2.3. Property `Machiavillager bridge protocol > oneOf > Welcome > protocol`

**Title:** Protocol

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Protocol version the backend agreed to speak. Always 1 today.

Specific value: `1`

## <a name="oneOf_i2"></a>3. Property `Machiavillager bridge protocol > oneOf > SpeakAction`

**Title:** SpeakAction

|                           |                     |
| ------------------------- | ------------------- |
| **Type**                  | `object`            |
| **Required**              | No                  |
| **Additional properties** | Not allowed         |
| **Defined in**            | #/$defs/SpeakAction |

**Description:** Backend asks the mod to speak a short sequence of lines as one action.

| Property                                        | Pattern | Type    | Deprecated | Definition           | Title/Description |
| ----------------------------------------------- | ------- | ------- | ---------- | -------------------- | ----------------- |
| + [v](#oneOf_i2_v )                             | No      | const   | No         | -                    | V                 |
| + [type](#oneOf_i2_type )                       | No      | const   | No         | -                    | Type              |
| + [request_id](#oneOf_i2_request_id )           | No      | string  | No         | -                    | Request Id        |
| + [action](#oneOf_i2_action )                   | No      | const   | No         | -                    | Action            |
| + [args](#oneOf_i2_args )                       | No      | object  | No         | In #/$defs/SpeakArgs | SpeakArgs         |
| + [timeout_ms](#oneOf_i2_timeout_ms )           | No      | integer | No         | -                    | Timeout Ms        |
| - [conversation_id](#oneOf_i2_conversation_id ) | No      | string  | No         | -                    | Conversation Id   |
| - [in_reply_to](#oneOf_i2_in_reply_to )         | No      | string  | No         | -                    | In Reply To       |

### <a name="oneOf_i2_v"></a>3.1. Property `Machiavillager bridge protocol > oneOf > SpeakAction > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

### <a name="oneOf_i2_type"></a>3.2. Property `Machiavillager bridge protocol > oneOf > SpeakAction > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action".

Specific value: `"action"`

### <a name="oneOf_i2_request_id"></a>3.3. Property `Machiavillager bridge protocol > oneOf > SpeakAction > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Generated by the backend: act_ followed by 16 random hex characters. Unique per action; every action_status for this action echoes it. Example: act_3fa9c01b7e2d4f60.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

### <a name="oneOf_i2_action"></a>3.4. Property `Machiavillager bridge protocol > oneOf > SpeakAction > action`

**Title:** Action

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Name of the allowlisted action. Only "speak" exists today.

Specific value: `"speak"`

### <a name="oneOf_i2_args"></a>3.5. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args`

**Title:** SpeakArgs

|                           |                   |
| ------------------------- | ----------------- |
| **Type**                  | `object`          |
| **Required**              | Yes               |
| **Additional properties** | Not allowed       |
| **Defined in**            | #/$defs/SpeakArgs |

**Description:** Arguments for speak. See SpeakArgs.

| Property                         | Pattern | Type            | Deprecated | Definition | Title/Description |
| -------------------------------- | ------- | --------------- | ---------- | ---------- | ----------------- |
| + [lines](#oneOf_i2_args_lines ) | No      | array of string | No         | -          | Lines             |
| - [to](#oneOf_i2_args_to )       | No      | string          | No         | -          | To                |

#### <a name="oneOf_i2_args_lines"></a>3.5.1. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args > lines`

**Title:** Lines

|              |                   |
| ------------ | ----------------- |
| **Type**     | `array of string` |
| **Required** | Yes               |

**Description:** What he says, in order. The mod paces the lines itself. Between 1 and 8 lines, each between 1 and 256 characters. Example: ["Hello, traveller."].

|                      | Array restrictions |
| -------------------- | ------------------ |
| **Min items**        | 1                  |
| **Max items**        | 8                  |
| **Items unicity**    | False              |
| **Additional items** | False              |
| **Tuple validation** | See below          |

| Each item of this array must be           | Description |
| ----------------------------------------- | ----------- |
| [lines items](#oneOf_i2_args_lines_items) | -           |

##### <a name="oneOf_i2_args_lines_items"></a>3.5.1.1. Machiavillager bridge protocol > oneOf > SpeakAction > args > lines > lines items

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | No       |

| Restrictions   |     |
| -------------- | --- |
| **Min length** | 1   |
| **Max length** | 256 |

#### <a name="oneOf_i2_args_to"></a>3.5.2. Property `Machiavillager bridge protocol > oneOf > SpeakAction > args > to`

**Title:** To

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | No       |
| **Format**   | `uuid`   |

**Description:** Private recipient. Set only for speech meant for one player: the lines reach that player alone, at any distance, and are never spoken aloud. Omit it for normal speech nearby. Explicit null is rejected. Example: "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69".

| Restrictions                      |                                                                                                                                                                                                                                                   |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$``` [Test](https://regex101.com/?regex=%5E%5B0-9a-fA-F%5D%7B8%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B12%7D%24) |

### <a name="oneOf_i2_timeout_ms"></a>3.6. Property `Machiavillager bridge protocol > oneOf > SpeakAction > timeout_ms`

**Title:** Timeout Ms

|              |           |
| ------------ | --------- |
| **Type**     | `integer` |
| **Required** | Yes       |

**Description:** How long the mod may take before the action is reported as failed with reason timeout, in milliseconds. Between 100 and 120000. Example: 30000.

| Restrictions |             |
| ------------ | ----------- |
| **Minimum**  | &ge; 100    |
| **Maximum**  | &le; 120000 |

### <a name="oneOf_i2_conversation_id"></a>3.7. Property `Machiavillager bridge protocol > oneOf > SpeakAction > conversation_id`

**Title:** Conversation Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | No       |

**Description:** Groups this speech with a conversation that the mod is tracking. Generated by the mod, see PlayerChatEvent. Omit it when the speech is not part of one. Explicit null is rejected.

| Restrictions                      |                                                                                                           |
| --------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^conv_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Econv_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

### <a name="oneOf_i2_in_reply_to"></a>3.8. Property `Machiavillager bridge protocol > oneOf > SpeakAction > in_reply_to`

**Title:** In Reply To

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | No       |

**Description:** The player_chat event this speech answers. Set it only for a reply, so the mod does not count the speech as unprompted: replies skip the unprompted-speech cooldown. Omit it for speech the backend starts on its own. Explicit null is rejected. Example: evt_01HZX8K2M9.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^evt_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eevt_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

## <a name="oneOf_i3"></a>4. Property `Machiavillager bridge protocol > oneOf > ActionStatus`

**Title:** ActionStatus

|                           |                  |
| ------------------------- | ---------------- |
| **Type**                  | `combining`      |
| **Required**              | No               |
| **Additional properties** | Any type allowed |

**Description:** Mod reports how an action request is going. The status selects the shape.

| One of(Option)                    |
| --------------------------------- |
| [Accepted](#oneOf_i3_oneOf_i0)    |
| [Running](#oneOf_i3_oneOf_i1)     |
| [Succeeded](#oneOf_i3_oneOf_i2)   |
| [Failed](#oneOf_i3_oneOf_i3)      |
| [Rejected](#oneOf_i3_oneOf_i4)    |
| [Interrupted](#oneOf_i3_oneOf_i5) |

### <a name="oneOf_i3_oneOf_i0"></a>4.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted`

**Title:** Accepted

|                           |                  |
| ------------------------- | ---------------- |
| **Type**                  | `object`         |
| **Required**              | No               |
| **Additional properties** | Not allowed      |
| **Defined in**            | #/$defs/Accepted |

**Description:** The mod took the request and will try to run it.

| Property                                       | Pattern | Type   | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i0_v )                   | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i0_type )             | No      | const  | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i0_request_id ) | No      | string | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i0_status )         | No      | const  | No         | -          | Status            |

#### <a name="oneOf_i3_oneOf_i0_v"></a>4.1.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i0_type"></a>4.1.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i0_request_id"></a>4.1.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i0_status"></a>4.1.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Accepted > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Progress. "accepted": the mod took the action and will try to run it.

Specific value: `"accepted"`

### <a name="oneOf_i3_oneOf_i1"></a>4.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running`

**Title:** Running

|                           |                 |
| ------------------------- | --------------- |
| **Type**                  | `object`        |
| **Required**              | No              |
| **Additional properties** | Not allowed     |
| **Defined in**            | #/$defs/Running |

**Description:** The mod has started performing the action.

| Property                                       | Pattern | Type   | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i1_v )                   | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i1_type )             | No      | const  | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i1_request_id ) | No      | string | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i1_status )         | No      | const  | No         | -          | Status            |

#### <a name="oneOf_i3_oneOf_i1_v"></a>4.2.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i1_type"></a>4.2.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i1_request_id"></a>4.2.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i1_status"></a>4.2.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Running > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Progress. "running": the mod has started the action.

Specific value: `"running"`

### <a name="oneOf_i3_oneOf_i2"></a>4.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded`

**Title:** Succeeded

|                           |                   |
| ------------------------- | ----------------- |
| **Type**                  | `object`          |
| **Required**              | No                |
| **Additional properties** | Not allowed       |
| **Defined in**            | #/$defs/Succeeded |

**Description:** The action finished. Nothing else to report.

| Property                                       | Pattern | Type   | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i2_v )                   | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i2_type )             | No      | const  | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i2_request_id ) | No      | string | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i2_status )         | No      | const  | No         | -          | Status            |

#### <a name="oneOf_i3_oneOf_i2_v"></a>4.3.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i2_type"></a>4.3.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i2_request_id"></a>4.3.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i2_status"></a>4.3.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Succeeded > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Terminal. "succeeded": the action finished. No other fields.

Specific value: `"succeeded"`

### <a name="oneOf_i3_oneOf_i3"></a>4.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed`

**Title:** Failed

|                           |                |
| ------------------------- | -------------- |
| **Type**                  | `object`       |
| **Required**              | No             |
| **Additional properties** | Not allowed    |
| **Defined in**            | #/$defs/Failed |

**Description:** The action could not be completed. The reason says why.

| Property                                       | Pattern | Type   | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i3_v )                   | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i3_type )             | No      | const  | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i3_request_id ) | No      | string | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i3_status )         | No      | const  | No         | -          | Status            |
| + [reason](#oneOf_i3_oneOf_i3_reason )         | No      | string | No         | -          | Reason            |

#### <a name="oneOf_i3_oneOf_i3_v"></a>4.4.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i3_type"></a>4.4.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i3_request_id"></a>4.4.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i3_status"></a>4.4.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Terminal. "failed": the action could not be completed.

Specific value: `"failed"`

#### <a name="oneOf_i3_oneOf_i3_reason"></a>4.4.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Failed > reason`

**Title:** Reason

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Why the action failed, in up to 200 characters. Set by the mod, or by the backend when the mod disconnects ("disconnected") or does not answer in time ("timeout"). Example: "disconnected".

| Restrictions   |     |
| -------------- | --- |
| **Max length** | 200 |

### <a name="oneOf_i3_oneOf_i4"></a>4.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected`

**Title:** Rejected

|                           |                  |
| ------------------------- | ---------------- |
| **Type**                  | `object`         |
| **Required**              | No               |
| **Additional properties** | Not allowed      |
| **Defined in**            | #/$defs/Rejected |

**Description:** The mod refused the request before starting it. The reason says why.

| Property                                       | Pattern | Type   | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------ | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i4_v )                   | No      | const  | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i4_type )             | No      | const  | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i4_request_id ) | No      | string | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i4_status )         | No      | const  | No         | -          | Status            |
| + [reason](#oneOf_i3_oneOf_i4_reason )         | No      | string | No         | -          | Reason            |

#### <a name="oneOf_i3_oneOf_i4_v"></a>4.5.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i4_type"></a>4.5.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i4_request_id"></a>4.5.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i4_status"></a>4.5.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Terminal. "rejected": the mod refused the action before starting it.

Specific value: `"rejected"`

#### <a name="oneOf_i3_oneOf_i4_reason"></a>4.5.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Rejected > reason`

**Title:** Reason

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Why the mod refused the action, in up to 200 characters. Example: "unknown action: run_command".

| Restrictions   |     |
| -------------- | --- |
| **Max length** | 200 |

### <a name="oneOf_i3_oneOf_i5"></a>4.6. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted`

**Title:** Interrupted

|                           |                     |
| ------------------------- | ------------------- |
| **Type**                  | `object`            |
| **Required**              | No                  |
| **Additional properties** | Not allowed         |
| **Defined in**            | #/$defs/Interrupted |

**Description:** The action started but stopped early. Reports how many lines were spoken.

| Property                                       | Pattern | Type    | Deprecated | Definition | Title/Description |
| ---------------------------------------------- | ------- | ------- | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i3_oneOf_i5_v )                   | No      | const   | No         | -          | V                 |
| + [type](#oneOf_i3_oneOf_i5_type )             | No      | const   | No         | -          | Type              |
| + [request_id](#oneOf_i3_oneOf_i5_request_id ) | No      | string  | No         | -          | Request Id        |
| + [status](#oneOf_i3_oneOf_i5_status )         | No      | const   | No         | -          | Status            |
| + [spoken](#oneOf_i3_oneOf_i5_spoken )         | No      | integer | No         | -          | Spoken            |
| + [of](#oneOf_i3_oneOf_i5_of )                 | No      | integer | No         | -          | Of                |

#### <a name="oneOf_i3_oneOf_i5_v"></a>4.6.1. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

#### <a name="oneOf_i3_oneOf_i5_type"></a>4.6.2. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "action_status".

Specific value: `"action_status"`

#### <a name="oneOf_i3_oneOf_i5_request_id"></a>4.6.3. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > request_id`

**Title:** Request Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The request_id of the action this status is about. Echoed from the action.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^act_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eact_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

#### <a name="oneOf_i3_oneOf_i5_status"></a>4.6.4. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > status`

**Title:** Status

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Terminal. "interrupted": the action started and stopped early, because a player talked over him.

Specific value: `"interrupted"`

#### <a name="oneOf_i3_oneOf_i5_spoken"></a>4.6.5. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > spoken`

**Title:** Spoken

|              |           |
| ------------ | --------- |
| **Type**     | `integer` |
| **Required** | Yes       |

**Description:** How many lines were spoken before the interruption. 0 or more. Example: 2.

| Restrictions |        |
| ------------ | ------ |
| **Minimum**  | &ge; 0 |

#### <a name="oneOf_i3_oneOf_i5_of"></a>4.6.6. Property `Machiavillager bridge protocol > oneOf > ActionStatus > oneOf > Interrupted > of`

**Title:** Of

|              |           |
| ------------ | --------- |
| **Type**     | `integer` |
| **Required** | Yes       |

**Description:** How many lines the action asked for, between 1 and 8. Example: 5.

| Restrictions |        |
| ------------ | ------ |
| **Minimum**  | &ge; 1 |
| **Maximum**  | &le; 8 |

## <a name="oneOf_i4"></a>5. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent`

**Title:** PlayerChatEvent

|                           |                         |
| ------------------------- | ----------------------- |
| **Type**                  | `object`                |
| **Required**              | No                      |
| **Additional properties** | Not allowed             |
| **Defined in**            | #/$defs/PlayerChatEvent |

**Description:** Mod reports that a player spoke. No reply is expected; the backend decides whether to act.

| Property                                        | Pattern | Type   | Deprecated | Definition                | Title/Description |
| ----------------------------------------------- | ------- | ------ | ---------- | ------------------------- | ----------------- |
| + [v](#oneOf_i4_v )                             | No      | const  | No         | -                         | V                 |
| + [type](#oneOf_i4_type )                       | No      | const  | No         | -                         | Type              |
| + [event_id](#oneOf_i4_event_id )               | No      | string | No         | -                         | Event Id          |
| + [event](#oneOf_i4_event )                     | No      | const  | No         | -                         | Event             |
| + [occurred_at](#oneOf_i4_occurred_at )         | No      | string | No         | -                         | Occurred At       |
| + [data](#oneOf_i4_data )                       | No      | object | No         | In #/$defs/PlayerChatData | PlayerChatData    |
| - [conversation_id](#oneOf_i4_conversation_id ) | No      | string | No         | -                         | Conversation Id   |

### <a name="oneOf_i4_v"></a>5.1. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

### <a name="oneOf_i4_type"></a>5.2. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "event".

Specific value: `"event"`

### <a name="oneOf_i4_event_id"></a>5.3. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > event_id`

**Title:** Event Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Generated by the mod: evt_ followed by 8 to 32 letters or digits. Unique per event. Example: evt_01HZX8K2M9.

| Restrictions                      |                                                                                                         |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^evt_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Eevt_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

### <a name="oneOf_i4_event"></a>5.4. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > event`

**Title:** Event

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Name of the event. Only "player_chat" exists today.

Specific value: `"player_chat"`

### <a name="oneOf_i4_occurred_at"></a>5.5. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > occurred_at`

**Title:** Occurred At

|              |             |
| ------------ | ----------- |
| **Type**     | `string`    |
| **Required** | Yes         |
| **Format**   | `date-time` |

**Description:** When the mod saw the chat, from the mod's clock, in UTC with the Z suffix. Example: 2026-10-18T12:00:00Z.

### <a name="oneOf_i4_data"></a>5.6. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data`

**Title:** PlayerChatData

|                           |                        |
| ------------------------- | ---------------------- |
| **Type**                  | `object`               |
| **Required**              | Yes                    |
| **Additional properties** | Not allowed            |
| **Defined in**            | #/$defs/PlayerChatData |

**Description:** Who said what. See PlayerChatData.

| Property                                     | Pattern | Type             | Deprecated | Definition | Title/Description |
| -------------------------------------------- | ------- | ---------------- | ---------- | ---------- | ----------------- |
| + [player_uuid](#oneOf_i4_data_player_uuid ) | No      | string           | No         | -          | Player Uuid       |
| + [player_name](#oneOf_i4_data_player_name ) | No      | string           | No         | -          | Player Name       |
| + [text](#oneOf_i4_data_text )               | No      | string           | No         | -          | Text              |
| + [addressed](#oneOf_i4_data_addressed )     | No      | enum (of string) | No         | -          | Addressed         |

#### <a name="oneOf_i4_data_player_uuid"></a>5.6.1. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > player_uuid`

**Title:** Player Uuid

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |
| **Format**   | `uuid`   |

**Description:** Minecraft UUID of the player who spoke, in canonical 8-4-4-4-12 form. Set by the mod. Example: "7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69".

| Restrictions                      |                                                                                                                                                                                                                                                   |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$``` [Test](https://regex101.com/?regex=%5E%5B0-9a-fA-F%5D%7B8%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B4%7D-%5B0-9a-fA-F%5D%7B12%7D%24) |

#### <a name="oneOf_i4_data_player_name"></a>5.6.2. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > player_name`

**Title:** Player Name

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** The player's in-game name, 1 to 16 characters. Example: "Luciano".

| Restrictions   |    |
| -------------- | -- |
| **Min length** | 1  |
| **Max length** | 16 |

#### <a name="oneOf_i4_data_text"></a>5.6.3. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > text`

**Title:** Text

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** What the player typed, 1 to 256 characters. Set by the mod. Chat text is never logged at INFO. Example: "hi".

| Restrictions   |     |
| -------------- | --- |
| **Min length** | 1   |
| **Max length** | 256 |

#### <a name="oneOf_i4_data_addressed"></a>5.6.4. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > data > addressed`

**Title:** Addressed

|              |                    |
| ------------ | ------------------ |
| **Type**     | `enum (of string)` |
| **Required** | Yes                |

**Description:** Why the mod forwarded this line. Set by the mod. "name": the player used his name. "conversation": the line continues a conversation the mod is tracking. "near": nearby speech without his name. "butt_in": the player talked over him while he was speaking. "private": the @m channel. The text is private and is never voice.

Must be one of:
* "name"
* "conversation"
* "near"
* "butt_in"
* "private"

### <a name="oneOf_i4_conversation_id"></a>5.7. Property `Machiavillager bridge protocol > oneOf > PlayerChatEvent > conversation_id`

**Title:** Conversation Id

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | No       |

**Description:** Generated by the mod: one id per conversation, new after 60 seconds of silence. Omit it when the chat is not part of a conversation. Explicit null is rejected. Example: conv_01HZX8K2M9.

| Restrictions                      |                                                                                                           |
| --------------------------------- | --------------------------------------------------------------------------------------------------------- |
| **Must match regular expression** | ```^conv_[A-Za-z0-9]{8,32}$``` [Test](https://regex101.com/?regex=%5Econv_%5BA-Za-z0-9%5D%7B8%2C32%7D%24) |

## <a name="oneOf_i5"></a>6. Property `Machiavillager bridge protocol > oneOf > Error`

**Title:** Error

|                           |               |
| ------------------------- | ------------- |
| **Type**                  | `object`      |
| **Required**              | No            |
| **Additional properties** | Not allowed   |
| **Defined in**            | #/$defs/Error |

**Description:** Either side reports a message it cannot accept. The code set is closed for branching.

| Property                      | Pattern | Type             | Deprecated | Definition | Title/Description |
| ----------------------------- | ------- | ---------------- | ---------- | ---------- | ----------------- |
| + [v](#oneOf_i5_v )           | No      | const            | No         | -          | V                 |
| + [type](#oneOf_i5_type )     | No      | const            | No         | -          | Type              |
| + [code](#oneOf_i5_code )     | No      | enum (of string) | No         | -          | Code              |
| + [detail](#oneOf_i5_detail ) | No      | string           | No         | -          | Detail            |

### <a name="oneOf_i5_v"></a>6.1. Property `Machiavillager bridge protocol > oneOf > Error > v`

**Title:** V

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Wire version of the message. Always 1. A breaking change bumps it.

Specific value: `1`

### <a name="oneOf_i5_type"></a>6.2. Property `Machiavillager bridge protocol > oneOf > Error > type`

**Title:** Type

|              |         |
| ------------ | ------- |
| **Type**     | `const` |
| **Required** | Yes     |

**Description:** Message kind. Always "error".

Specific value: `"error"`

### <a name="oneOf_i5_code"></a>6.3. Property `Machiavillager bridge protocol > oneOf > Error > code`

**Title:** Code

|              |                    |
| ------------ | ------------------ |
| **Type**     | `enum (of string)` |
| **Required** | Yes                |

**Description:** Why the message was refused, from a closed set the other side can branch on. bad_message: could not be parsed or broke a rule. unsupported_protocol: the protocol version is not supported. unauthorized: the token was wrong or missing. internal: the sender failed unexpectedly.

Must be one of:
* "bad_message"
* "unsupported_protocol"
* "unauthorized"
* "internal"

### <a name="oneOf_i5_detail"></a>6.4. Property `Machiavillager bridge protocol > oneOf > Error > detail`

**Title:** Detail

|              |          |
| ------------ | -------- |
| **Type**     | `string` |
| **Required** | Yes      |

**Description:** Short human-readable explanation, up to 500 characters. It never repeats the rejected payload. Example: "action args do not match the speak schema".

| Restrictions   |     |
| -------------- | --- |
| **Max length** | 500 |

----------------------------------------------------------------------------------------------------------------------------
