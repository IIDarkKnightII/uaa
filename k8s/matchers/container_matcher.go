package matchers

import (
	"fmt"
	. "github.com/onsi/gomega"
	"github.com/onsi/gomega/format"
	. "github.com/onsi/gomega/gstruct"
	"github.com/onsi/gomega/types"
	coreV1 "k8s.io/api/core/v1"
	k8sResource "k8s.io/apimachinery/pkg/api/resource"
)

type ContainerMatcher struct {
	fields  map[string]types.GomegaMatcher
	envVars Elements

	container *coreV1.Container
	executed  types.GomegaMatcher
}

func NewContainerMatcher() *ContainerMatcher {
	return &ContainerMatcher{map[string]types.GomegaMatcher{}, Elements{}, nil, nil}
}

func (matcher *ContainerMatcher) WithName(name string) *ContainerMatcher {
	matcher.fields["Name"] = Equal(name)

	return matcher
}

func (matcher *ContainerMatcher) WithImage(image string) *ContainerMatcher {
	matcher.fields["Image"] = Equal(image)

	return matcher
}

func (matcher *ContainerMatcher) WithImageContaining(image string) *ContainerMatcher {
	matcher.fields["Image"] = ContainSubstring(image)

	return matcher
}

func (matcher *ContainerMatcher) WithEnvVar(name, value string) *ContainerMatcher {
	matcher.envVars[name] = MatchFields(IgnoreExtras, Fields{
		"Value": Equal(value),
	})

	return matcher
}

func (matcher *ContainerMatcher) WithResourceRequests(memory, cpu string) *ContainerMatcher {
	resourceList := coreV1.ResourceList{}
	resourceList[coreV1.ResourceMemory] = k8sResource.MustParse(memory)
	resourceList[coreV1.ResourceCPU] = k8sResource.MustParse(cpu)
	matcher.fields["Resources"] = MatchFields(IgnoreExtras, Fields{
		"Requests": Equal(resourceList),
	})
	return matcher
}

func (matcher *ContainerMatcher) Match(actual interface{}) (bool, error) {
	container, ok := actual.(coreV1.Container)
	if !ok {
		return false, fmt.Errorf("Expected a container. Got\n%s", format.Object(actual, 1))
	}

	matcher.container = &container

	identifyEnvVarByName := func(element interface{}) string {
		return element.(coreV1.EnvVar).Name
	}
	matcher.fields["Env"] = MatchElements(identifyEnvVarByName, IgnoreExtras, matcher.envVars)

	matcher.executed = MatchFields(IgnoreExtras, matcher.fields)
	return matcher.executed.Match(container)
}

func (matcher *ContainerMatcher) FailureMessage(actual interface{}) string {
	return fmt.Sprintf(
		"At least one container should match: \n%s",
		matcher.executed.FailureMessage(&matcher.container),
	)
}

func (matcher *ContainerMatcher) NegatedFailureMessage(actual interface{}) string {
	return fmt.Sprintf(
		"No container should match: \n%s",
		matcher.executed.FailureMessage(&matcher.container),
	)
}
