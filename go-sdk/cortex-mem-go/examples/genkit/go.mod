module github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/examples/genkit

go 1.22

require (
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go v0.0.0
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/genkit v0.0.0-00010101000000-000000000000
)

replace (
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go => ../..
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/eino => ../../eino
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/genkit => ../../genkit
	github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/langchaingo => ../../langchaingo
)
