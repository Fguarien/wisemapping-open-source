# Built-in AI copilot

WiseMapping can generate a new mind map from a prompt by calling any **OpenAI-compatible chat
completions endpoint**: OpenAI itself, a self-hosted model served by Ollama or vLLM, or a proxy
such as LiteLLM. The copilot is **disabled by default**; the "AI Copilot" button then keeps
opening the hosted ChatGPT copilot, exactly as before.

## How it works

1. The user describes the map in the copilot dialog and, when several models are configured,
   picks one.
2. `POST /api/restful/ai/copilot/maps` (authenticated) sends the prompt to
   `{base-url}/chat/completions`, asking for one JSON object: a title and a tree of topics.
3. The answer is parsed leniently (a Markdown code fence or a `<think>` block is tolerated),
   bounded (depth, number of topics, topic length) and converted to the WiseMapping map format.
   An unusable answer is retried once, then reported as an error.
4. The map is stored as a new private map of the user, and the dialog offers to open it.

## Where the prompt goes: locality

Every model is declared **local** (it runs on infrastructure you control: prompts stay in your
network) or **external** (prompts are sent to a third party). Users see it before generating:

| Locality | Badge | Generate button |
| --- | --- | --- |
| `local` | green, house icon, "Local — stays in your network" | "Generate" |
| `external` | orange, globe icon, "External — sent to {provider}" | "Send to {provider} and generate" |

The badge always carries an icon and a sentence; colour is never the only signal.

### Checking where the answer actually came from

A proxy can serve a **fallback** under the requested name: LiteLLM, for instance, may answer a
request for a cloud model with a local one, or the other way round, and say nothing. Two settings
deal with it:

- `disable-fallbacks: true` sends `"disable_fallbacks": true` with every request, so that LiteLLM
  fails instead of silently serving another model. **This is the prevention.** Leave it off for
  endpoints that reject unknown parameters (OpenAI does).
- `served-match` (per model) is a regular expression the `model` field of the completion must
  **fully** match. **This is detection**: when the answer comes back, the prompt has already been
  sent.
  - match: the declared locality is confirmed, and the dialog says "Generated locally by …" or
    "Generated externally by …";
  - a model declared **local** that is answered by anything else, or whose answer has no `model`
    field, is **rejected**: no map is created, the user gets an error and the server logs a
    warning. The user was promised that the prompt would stay in the network; handing them a map
    as if nothing happened would hide that it may not have;
  - a model declared **external** that is answered by something else is accepted, with a warning
    shown to the user (the data left the network either way, as they were told);
  - without `served-match`, the locality is reported as unverified.

Calibrate `served-match` by sending one request and reading the `model` field of the answer: a
proxy usually reports the deployment it called (for LiteLLM, the `model` of the deployment, e.g.
`hosted_vllm/qwen3.5:9b`), not the alias that was requested.

## Configuration

Properties live under `app.ai.copilot` (see `application.yml`). With Spring Boot relaxed binding the
environment variables drop the dashes, and list entries are indexed:

| Property | Environment variable | Default |
| --- | --- | --- |
| `enabled` | `APP_AI_COPILOT_ENABLED` | `false` |
| `base-url` | `APP_AI_COPILOT_BASEURL` | — e.g. `https://api.openai.com/v1`, `http://litellm:4000/v1` |
| `api-key` | `APP_AI_COPILOT_APIKEY` | — bearer token; supply it through the environment |
| `models[n].id` | `APP_AI_COPILOT_MODELS_0_ID` | — model name sent to the endpoint |
| `models[n].label` | `APP_AI_COPILOT_MODELS_0_LABEL` | the id |
| `models[n].locality` | `APP_AI_COPILOT_MODELS_0_LOCALITY` | `external` |
| `models[n].provider` | `APP_AI_COPILOT_MODELS_0_PROVIDER` | — shown for external models |
| `models[n].served-match` | `APP_AI_COPILOT_MODELS_0_SERVEDMATCH` | — |
| `disable-fallbacks` | `APP_AI_COPILOT_DISABLEFALLBACKS` | `false` |
| `timeout-seconds` | `APP_AI_COPILOT_TIMEOUTSECONDS` | `90` |
| `max-prompt-length` | `APP_AI_COPILOT_MAXPROMPTLENGTH` | `2000` |
| `max-topics` | `APP_AI_COPILOT_MAXTOPICS` | `60` |
| `max-depth` | `APP_AI_COPILOT_MAXDEPTH` | `3` |
| `temperature` | `APP_AI_COPILOT_TEMPERATURE` | `0.3` |

The first model of the list is the default. A single-model form (`model`, `locality`, `provider`,
`served-match` directly under `app.ai.copilot`) is still accepted when no list is given.

`/api/restful/app/config` exposes `copilotEnabled` and `copilotModels` (`id`, `label`, `locality`,
`provider`) — never the endpoint, the key or the match expressions. Generations are rate-limited by
the `copilotLimiter` Resilience4j instance (10 per minute for the whole instance by default).

### Example: OpenAI

```yaml
app:
  ai:
    copilot:
      enabled: true
      base-url: https://api.openai.com/v1
      models:
        - id: gpt-4o-mini
          label: GPT-4o mini
          locality: external
          provider: OpenAI
          served-match: "gpt-4o-mini.*"
```

### Example: LiteLLM with a local model and an external one

```sh
APP_AI_COPILOT_ENABLED=true
APP_AI_COPILOT_BASEURL=http://litellm:4000/v1
APP_AI_COPILOT_APIKEY=<virtual key>
APP_AI_COPILOT_DISABLEFALLBACKS=true
APP_AI_COPILOT_MODELS_0_ID=qwen-local
APP_AI_COPILOT_MODELS_0_LABEL=Qwen 3.5 9B (on premises)
APP_AI_COPILOT_MODELS_0_LOCALITY=local
APP_AI_COPILOT_MODELS_0_SERVEDMATCH=(hosted_vllm/)?qwen3\.5:9b
APP_AI_COPILOT_MODELS_1_ID=mistral-small
APP_AI_COPILOT_MODELS_1_LOCALITY=external
APP_AI_COPILOT_MODELS_1_PROVIDER=Mistral AI
APP_AI_COPILOT_MODELS_1_SERVEDMATCH=mistral-small.*
```

## Errors

| HTTP | Cause |
| --- | --- |
| 400 | empty or too long prompt, unknown model |
| 404 | copilot not configured |
| 429 | rate limit reached |
| 502 | unusable answer, or a local model answered from elsewhere (no map created) |
| 503 | endpoint unreachable, timeout or error answer |
