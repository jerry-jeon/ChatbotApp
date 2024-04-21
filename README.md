# Conversational Chatbot App

![preview](/images/preview.gif)

The Conversational Chatbot App is a chatbot app that can answer questions based on the knowledge base. 

## Features of the App

- **Conversation UI**: The app is designed to be used in a conversational way. This is a more natural way to interact with the app.
- **Voice UI**: The app will support voice commands. This is a more natural way to interact with the app.
- **Local caching**: The app will cache data locally. This will allow the app to work offline.

## Features of the Chatbot

- Knowledge Base: The chatbot can answer questions based on the knowledge base.
  - URL, File can be used as knowledge base.
- Supported various engines
  - GPT-4
  - Claude 3
  - Llama 2 70B
  - and more!
- Function calls : The chatbot can call functions (HTTP call) to get the answer.
- Response Workflow : custom response workflow can be defined

# Getting Started
## Prerequisites to use the App

- Sendbird Account
- Sendbird Application
- Sendbird Bot

### How to set up Sendbird
1. Go to [Sendbird](https://sendbird.com/products/ai-chatbot) and create an account.
![Sendbird signup](/images/1-sendbird-signup.png)
2. Create an organization and an application.

<p align="center">
  <img src="images/2-sendbird-org.png" width="300" />
  <img src="images/3-sendbird-app.png" width="300" /> 
</p>

3. Create a bot. You can ingest the knowledge base.
![Sendbird create bot](/images/4-sendbird-bot.png)

4. Get the `APP_ID` in the [Dashboard](https://dashboard.sendbird.com/).
![Sendbird app id](/images/5-sendbird-app-id.png)
5. Enter the `Users` tab and create a user.
<img src="/images/6-sendbird-user.png" alt="Create user" height="500">


## How to use the App

### [Method 1] Build and Run the App
1. In the root directory, create a `secrets.properties` file.
2. Add the following properties to the `secrets.properties` file.
```
sendbirdAppId="YOUR_SENDBIRD_APP_ID"
sendbirdUserId="YOUR_SENDBIRD_USER_ID"
```
3. Run the app.

### [Method 2] Use the App
1. Install the apk file from the release.
2. Open the app.
3. Enter the `APP_ID` and `USER_ID` to the input fields.
<img src="/images/7-app-input.jpg" alt="APP_ID, USER_ID inputs" height="500">
