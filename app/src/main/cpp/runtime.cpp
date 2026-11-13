#include <string>

// Linking the shared C++ runtime packages it for FFmpeg's native dependencies.
extern "C" int skachay_runtime_available() {
    return static_cast<int>(std::string("ok").size());
}
