namespace CallRecorderMobileApp.Api.Services;

public interface IRecordingStorageService
{
    Task<string> SaveAsync(Guid recordingId, IFormFile file);
}

// Basit yerel disk depolama örneği. Prodüksiyonda S3/Azure Blob/Oracle Object Storage
// gibi bir çözüme geçirin ve bu arayüzü aynı şekilde implemente edin.
public class LocalDiskStorageService : IRecordingStorageService
{
    private readonly string _rootPath;

    public LocalDiskStorageService(IConfiguration configuration)
    {
        _rootPath = configuration["Storage:RootPath"] ?? "/var/callrecorder/storage";
        Directory.CreateDirectory(_rootPath);
    }

    public async Task<string> SaveAsync(Guid recordingId, IFormFile file)
    {
        var fileName = $"{recordingId}.m4a";
        var fullPath = Path.Combine(_rootPath, fileName);

        await using var stream = new FileStream(fullPath, FileMode.Create);
        await file.CopyToAsync(stream);

        return fullPath;
    }
}
