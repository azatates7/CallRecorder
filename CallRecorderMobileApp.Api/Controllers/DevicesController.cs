using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using CallRecorderMobileApp.Api.Data;
using CallRecorderMobileApp.Api.Models;

namespace CallRecorderMobileApp.Api.Controllers;

[ApiController]
[Route("api/devices")]
public class DevicesController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly ILogger<DevicesController> _logger;

    public DevicesController(AppDbContext db, ILogger<DevicesController> logger)
    {
        _db = db;
        _logger = logger;
    }

    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterDeviceRequest request)
    {
        var existing = await _db.Devices.FirstOrDefaultAsync(d => d.DeviceId == request.DeviceId);

        if (existing == null)
        {
            _db.Devices.Add(new Device
            {
                DeviceId = request.DeviceId,
                FcmToken = request.FcmToken,
                Platform = request.Platform,
            });
            _logger.LogInformation("Yeni cihaz kaydedildi: {DeviceId}", request.DeviceId);
        }
        else
        {
            existing.FcmToken = request.FcmToken;
            existing.LastSeenAt = DateTime.UtcNow;
        }

        await _db.SaveChangesAsync();
        return Ok(new { success = true });
    }
}
