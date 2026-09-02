using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using CallRecorder.Api.Data;
using CallRecorder.Api.Services;

namespace CallRecorder.Api.Controllers;

public record SendNotificationRequest(string DeviceId, string Title, string Body);

[ApiController]
[Route("api/notifications")]
public class NotificationsController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly IFcmNotificationService _fcm;

    public NotificationsController(AppDbContext db, IFcmNotificationService fcm)
    {
        _db = db;
        _fcm = fcm;
    }

    [HttpPost("send")]
    public async Task<IActionResult> Send([FromBody] SendNotificationRequest request)
    {
        var device = await _db.Devices.FirstOrDefaultAsync(d => d.DeviceId == request.DeviceId);
        if (device == null) return NotFound("Cihaz bulunamadı");

        var messageId = await _fcm.SendToDeviceAsync(device.FcmToken, request.Title, request.Body);
        return Ok(new { success = true, messageId });
    }
}
