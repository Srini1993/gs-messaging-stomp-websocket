// File: src/main/resources/static/app.js
const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
const host = window.location.host;
const brokerURL = protocol + host + '/gs-guide-websocket';
let username = null;
const stompClient = new StompJs.Client({
    brokerURL: brokerURL
});
stompClient.onConnect = (frame) => {
    setConnected(true);
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/greetings', (greeting) => {
        showGreeting(JSON.parse(greeting.body).content);
    });
};
stompClient.onWebSocketError = (error) => {
    console.error('Error with websocket', error);
};
stompClient.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message']);
    console.error('Additional details: ' + frame.body);
};
function setConnected(connected) {
    if (connected) {
        // Hide name input and show message input and current user
        $('#username-container').hide();
        $('#user-display').show();
        $('#message-container').show();
        $('#send').prop('disabled', false);
        $('#currentUser').text(username);
    } else {
        $('#username-container').show();
        $('#user-display').hide();
        $('#message-container').hide();
        $('#send').prop('disabled', true);
    }
    $('#greetings').html('');
}
function connect() {
    username = $('#username').val();
    if (!username) {
        alert('Please enter your name.');
        return;
    }
    stompClient.activate();
}
function disconnect() {
    stompClient.deactivate();
    setConnected(false);
    username = null;
    $('#username').val('');
    console.log('Disconnected');
}
function sendMessage() {
    const text = $('#message').val();
    const fullMessage = username + ': ' + text;
    stompClient.publish({
        destination: '/app/hello',
        body: JSON.stringify({ 'name': fullMessage })
    });
    $('#message').val('');
}
function showGreeting(message) {
    $('#greetings').append('<tr><td>' + message + '</td></tr>');
}
$(function () {
    $('form').on('submit', (e) => e.preventDefault());
    $('#connect').click(() => connect());
    $('#disconnect').click(() => disconnect());
    $('#send').click(() => sendMessage());
});