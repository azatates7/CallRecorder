using FirebaseAdmin;
using FirebaseAdmin.Messaging;
using Google.Apis.Auth.OAuth2;

namespace CallRecorder.Api.Services;

public interface IFcmNotificationService
{
    Task<string> SendToDeviceAsync(string fcmToken, string title, string body, Dictionary<string, string>? data = null);
}

public class FcmNotificationService : IFcmNotificationService
{
    public FcmNotificationService(IConfiguration configuration)
    {
        if (FirebaseApp.DefaultInstance == null)
        {
            var credentialsPath = configuration["Firebase:CredentialsPath"]
                ?? throw new InvalidOperationException("Firebase:CredentialsPath ayarlanmamış");

            FirebaseApp.Create(new AppOptions
            {
                Credential = GoogleCredential.FromFile(credentialsPath)
            });
        }
    }

    public async Task<string> SendToDeviceAsync(string fcmToken, string title, string body, Dictionary<string, string>? data = null)
    {
        var message = new Message
        {
            Token = fcmToken,
            Notification = new Notification
            {
                Title = title,
                Body = body
            },
            Data = data,
            Android = new AndroidConfig
            {
                Priority = Priority.High
            }
        };

        return await FirebaseMessaging.DefaultInstance.SendAsync(message);
    }
}
